// 기사 제목 분석 접수와 Polling API
import type { TitleResultViewData } from '../types/pageData'
import { ArticleChangedError } from './analysisErrors'

interface AcceptedResponse {
  analysisId: string
  deadlineAt: string
  pollAfterSeconds: number
  guestAccessToken?: string
}

interface ProgressResponse {
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED'
  pollAfterSeconds?: number
  result?: TitleResultViewData
  error?: { code?: string; detail?: string }
}

interface HeadlineAnalysisOptions {
  reanalyze?: boolean
}

const readCookie = (name: string) => {
  const prefix = `${name}=`
  const cookie = document.cookie
    .split(';')
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix))

  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
}

const readJson = async <T>(response: Response): Promise<T> =>
  (await response.json()) as T

const wait = (seconds: number) =>
  new Promise<void>((resolve) => {
    window.setTimeout(resolve, Math.max(0, seconds) * 1000)
  })

/** CSRF Cookie 발급 후 제목 분석 작업 접수 */
const acceptHeadlineAnalysis = async (
  articleUrl: string,
  options: HeadlineAnalysisOptions,
): Promise<AcceptedResponse> => {
  const csrfResponse = await fetch('/api/csrf', { credentials: 'include' })
  if (!csrfResponse.ok) {
    throw new Error('분석을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  }

  const csrfToken = readCookie('XSRF-TOKEN')
  if (!csrfToken) {
    throw new Error('분석을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  }

  const headers = new Headers()
  headers.set('Content-Type', 'application/json')
  headers.set('X-XSRF-TOKEN', csrfToken)
  const response = await fetch('/api/analyses/headline', {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(
      options.reanalyze ? { articleUrl, reanalyze: true } : { articleUrl },
    ),
  })
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as {
      code?: string
    }
    throw new Error(
      problem.code === 'ANALYSIS_REQUEST_RATE_LIMIT_EXCEEDED'
        ? '요청이 너무 많습니다. 1분 후 다시 시도해 주세요.'
        : '기사 제목을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    )
  }

  return readJson<AcceptedResponse>(response)
}

/** 완료 또는 실패까지 제목 분석 상태 조회 */
const pollHeadlineAnalysis = async (
  articleUrl: string,
  accepted: AcceptedResponse,
): Promise<TitleResultViewData> => {
  let pollAfterSeconds = accepted.pollAfterSeconds
  const deadline = Date.parse(accepted.deadlineAt)

  while (!Number.isFinite(deadline) || Date.now() <= deadline) {
    await wait(pollAfterSeconds)
    const requestInit: RequestInit = { credentials: 'include' }
    if (accepted.guestAccessToken) {
      const headers = new Headers()
      headers.set('X-Analysis-Access-Token', accepted.guestAccessToken)
      requestInit.headers = headers
    }
    const response = await fetch(
      `/api/analyses/headline/${encodeURIComponent(accepted.analysisId)}`,
      requestInit,
    )
    if (!response.ok) {
      throw new Error(
        '기사 제목을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
    }

    const progress = await readJson<ProgressResponse>(response)
    if (progress.status === 'COMPLETED' && progress.result) {
      return { ...progress.result, analysisId: accepted.analysisId }
    }
    if (progress.status === 'FAILED') {
      if (progress.error?.code === 'ARTICLE_CHANGED') {
        throw new ArticleChangedError(articleUrl)
      }
      throw new Error(
        progress.error?.detail ??
          '기사 제목을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
    }
    pollAfterSeconds = progress.pollAfterSeconds ?? pollAfterSeconds
  }

  throw new Error('분석 제한 시간을 초과했습니다. 다시 시도해 주세요.')
}

/** 클립보드 기사 URL의 제목 분석 완료 결과 조회 */
export const analyzeHeadline = async (
  articleUrl: string,
  options: HeadlineAnalysisOptions = {},
): Promise<TitleResultViewData> =>
  pollHeadlineAnalysis(articleUrl, await acceptHeadlineAnalysis(articleUrl, options))
