// 건강 기사 분석 접수와 Polling API
import type {
  AnalysisStage,
  HealthAnalysisViewData,
  HealthClaimStatus,
  HealthResultViewData,
} from '../types/pageData'

interface AcceptedResponse {
  analysisId: string
  deadlineAt: string
  pollAfterSeconds: number
  guestAccessToken?: string
}

type BackendStage =
  | 'QUEUED'
  | 'CHECKING_ARTICLE'
  | 'SEARCHING_EVIDENCE'
  | 'GENERATING_RESULT'
  | 'COMPLETED'
  | 'FAILED'

interface BackendEvidence {
  title: string
  provider: string
  publishedOrUpdatedDate?: string
  sourceUrl: string
  summary: string
}

interface BackendClaim {
  order: number
  claim: string
  status: HealthClaimStatus
  reason: string
  evidences: BackendEvidence[]
}

interface BackendHealthResult {
  article: {
    url: string
    title: string
    publisher: string
    publishedAt?: string
  }
  analyzedAt: string
  claims: BackendClaim[]
}

interface ProgressResponse {
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED'
  stage: BackendStage
  pollAfterSeconds?: number
  result?: BackendHealthResult
  error?: ApiErrorResponse
}

interface ApiErrorResponse {
  code?: string
}

interface HealthAnalysisOptions {
  signal?: AbortSignal
  onProgress?: (data: HealthAnalysisViewData) => void
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

const DEFAULT_ERROR_MESSAGE =
  '건강 기사를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.'

const HEALTH_ANALYSIS_ERROR_MESSAGES: Record<string, string> = {
  ARTICLE_NOT_HEALTH_RELATED: '건강·의학·보건 관련 기사로 확인되지 않았습니다.',
  ARTICLE_TOPIC_UNCERTAIN: '건강·의학·보건 관련 기사인지 확인하기 어렵습니다.',
  UNSUPPORTED_PUBLISHER: '현재 지원하지 않는 언론사입니다.',
  PUBLISHER_TEMPORARILY_DISABLED: '현재 일시적으로 지원하지 않는 언론사입니다.',
  DAILY_LIMIT_EXCEEDED:
    '오늘 사용할 수 있는 건강 분석 횟수를 모두 사용했습니다.',
  ANALYSIS_DEADLINE_EXCEEDED:
    '분석 제한 시간을 초과했습니다. 다시 시도해 주세요.',
  ANALYSIS_SERVICE_UNAVAILABLE:
    '분석 서비스를 현재 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.',
}

const toHealthAnalysisErrorMessage = (code?: string) =>
  (code ? HEALTH_ANALYSIS_ERROR_MESSAGES[code] : undefined) ??
  DEFAULT_ERROR_MESSAGE

const readErrorCode = async (response: Response) => {
  try {
    return (await readJson<ApiErrorResponse>(response)).code
  } catch {
    return undefined
  }
}

const wait = (seconds: number, signal?: AbortSignal) =>
  new Promise<void>((resolve, reject) => {
    const cancel = () =>
      reject(new DOMException('Health analysis cancelled', 'AbortError'))
    if (signal?.aborted) {
      cancel()
      return
    }

    const timeoutId = window.setTimeout(
      () => {
        signal?.removeEventListener('abort', abort)
        resolve()
      },
      Math.max(0, seconds) * 1000,
    )
    const abort = () => {
      window.clearTimeout(timeoutId)
      cancel()
    }
    signal?.addEventListener('abort', abort, { once: true })
  })

const toViewStage = (stage: BackendStage): AnalysisStage => {
  if (stage === 'SEARCHING_EVIDENCE') {
    return 'EVIDENCE_SEARCHING'
  }
  if (stage === 'GENERATING_RESULT') {
    return 'RESULT_PREPARING'
  }
  return 'ARTICLE_CHECKED'
}

const toProgressView = (
  articleUrl: string,
  stage: BackendStage,
): HealthAnalysisViewData => ({
  article: {
    url: articleUrl,
    title: '기사 제목 확인 중',
    publisher: '기사 확인 중',
  },
  stage: toViewStage(stage),
})

const toResultView = (result: BackendHealthResult): HealthResultViewData => {
  const primaryClaim = result.claims[0]
  if (!primaryClaim) {
    throw new Error('분석 결과를 확인하지 못했습니다. 다시 시도해 주세요.')
  }

  return {
    article: result.article,
    analyzedAt: result.analyzedAt,
    claim: primaryClaim.claim,
    claimStatus: primaryClaim.status,
    reasons: [primaryClaim.reason],
    evidences: primaryClaim.evidences.map((evidence, index) => ({
      ...evidence,
      id: `${primaryClaim.order}-${index + 1}`,
    })),
  }
}

/** CSRF Cookie 발급 후 건강 분석 작업 접수 */
const acceptHealthAnalysis = async (
  articleUrl: string,
  signal?: AbortSignal,
): Promise<AcceptedResponse> => {
  const csrfResponse = await fetch('/api/csrf', {
    credentials: 'include',
    ...(signal ? { signal } : {}),
  })
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
  const response = await fetch('/api/analyses/health', {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify({ articleUrl }),
    ...(signal ? { signal } : {}),
  })
  if (!response.ok) {
    throw new Error(toHealthAnalysisErrorMessage(await readErrorCode(response)))
  }

  return readJson<AcceptedResponse>(response)
}

/** 완료 또는 실패까지 건강 분석 상태 조회 */
const pollHealthAnalysis = async (
  articleUrl: string,
  accepted: AcceptedResponse,
  options: HealthAnalysisOptions,
): Promise<HealthResultViewData> => {
  let pollAfterSeconds = accepted.pollAfterSeconds
  const deadline = Date.parse(accepted.deadlineAt)

  while (!Number.isFinite(deadline) || Date.now() <= deadline) {
    await wait(pollAfterSeconds, options.signal)
    const requestInit: RequestInit = {
      credentials: 'include',
    }
    if (options.signal) {
      requestInit.signal = options.signal
    }
    if (accepted.guestAccessToken) {
      const headers = new Headers()
      headers.set('X-Analysis-Access-Token', accepted.guestAccessToken)
      requestInit.headers = headers
    }
    const response = await fetch(
      `/api/analyses/health/${encodeURIComponent(accepted.analysisId)}`,
      requestInit,
    )
    if (!response.ok) {
      throw new Error(
        toHealthAnalysisErrorMessage(await readErrorCode(response)),
      )
    }

    const progress = await readJson<ProgressResponse>(response)
    if (progress.status === 'COMPLETED' && progress.result) {
      return toResultView(progress.result)
    }
    if (progress.status === 'FAILED') {
      throw new Error(toHealthAnalysisErrorMessage(progress.error?.code))
    }
    options.onProgress?.(toProgressView(articleUrl, progress.stage))
    pollAfterSeconds = progress.pollAfterSeconds ?? pollAfterSeconds
  }

  throw new Error('분석 제한 시간을 초과했습니다. 다시 시도해 주세요.')
}

/** 클립보드 건강 기사 URL의 분석 완료 결과 조회 */
export const analyzeHealthArticle = async (
  articleUrl: string,
  options: HealthAnalysisOptions = {},
): Promise<HealthResultViewData> => {
  options.onProgress?.(toProgressView(articleUrl, 'QUEUED'))
  const accepted = await acceptHealthAnalysis(articleUrl, options.signal)
  return pollHealthAnalysis(articleUrl, accepted, options)
}
