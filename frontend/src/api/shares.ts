// 분석 결과 공유 API
import type { ArticleViewData, HeadlineIssueType } from '../types/pageData'

export type ShareType = 'HEALTH' | 'HEADLINE'

export interface CreatedShare {
  shareType: ShareType
  shareToken: string
  expiresAt: string
}

export interface SharedHealthEvidence {
  sourceKind: 'OFFICIAL' | 'PUBMED'
  title: string
  provider: string
  publishedOrUpdatedDate?: string
  sourceUrl: string
  summary: string
}

export interface SharedHealthClaim {
  order: number
  claim: string
  status: 'SUPPORTED' | 'NEEDS_REVIEW' | 'CONTRADICTED' | 'INSUFFICIENT'
  reason: string
  evidences: SharedHealthEvidence[]
}

export interface SharedHealthResult {
  shareType: 'HEALTH'
  expiresAt: string
  article: ArticleViewData
  analyzedAt: string
  overallStatus: 'RELIABLE' | 'CAUTION' | 'DOUBTFUL'
  confirmationRate: string
  confirmedClaimCount: number
  totalClaimCount: number
  claims: SharedHealthClaim[]
  expertReviewStatus: 'NOT_REVIEWED' | 'REVIEWED'
  limitedEvidence: boolean
}

export interface SharedHeadlineResult {
  shareType: 'HEADLINE'
  expiresAt: string
  article: ArticleViewData
  analyzedAt: string
  issues: Array<{ type: HeadlineIssueType; explanation: string }>
  alternativeHeadline?: string
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

const csrfToken = async () => {
  const response = await fetch('/api/csrf', { credentials: 'include' })
  const token = readCookie('XSRF-TOKEN')
  if (!response.ok || !token) {
    throw new Error('공유 요청을 시작하지 못했습니다. 다시 시도해 주세요.')
  }
  return token
}

const shareError = (response: Response, fallback: string) => {
  if (response.status === 401 || response.status === 403) {
    return '로그인 후 결과를 공유할 수 있습니다.'
  }
  return fallback
}

const createShare = async (path: string): Promise<CreatedShare> => {
  const token = await csrfToken()
  const response = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: { 'X-XSRF-TOKEN': token },
  })
  if (!response.ok) {
    throw new Error(
      shareError(
        response,
        '공유 링크를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.',
      ),
    )
  }
  return readJson<CreatedShare>(response)
}

/** 저장 건강 분석 공유 생성 */
export const createHealthShare = (recordId: string) =>
  createShare(`/api/health-records/${encodeURIComponent(recordId)}/shares`)

/** 완료 제목 분석 공유 생성 */
export const createHeadlineShare = (analysisId: string) =>
  createShare(`/api/analyses/headline/${encodeURIComponent(analysisId)}/shares`)

const getSharedResult = async <T>(path: string, token: string): Promise<T> => {
  const response = await fetch(path, {
    cache: 'no-store',
    headers: { 'X-Share-Token': token },
  })
  if (!response.ok) {
    throw new Error('공유 결과를 확인할 수 없습니다.')
  }
  return readJson<T>(response)
}

/** 공개 건강 공유 조회 */
export const getSharedHealthResult = (token: string) =>
  getSharedResult<SharedHealthResult>('/api/shares/health', token)

/** 공개 제목 공유 조회 */
export const getSharedHeadlineResult = (token: string) =>
  getSharedResult<SharedHeadlineResult>('/api/shares/headline', token)

const revokeShare = async (path: string, token: string) => {
  const csrf = await csrfToken()
  const headers = new Headers()
  headers.set('X-XSRF-TOKEN', csrf)
  headers.set('X-Share-Token', token)
  const response = await fetch(path, {
    method: 'DELETE',
    credentials: 'include',
    headers,
  })
  if (!response.ok) {
    throw new Error(
      shareError(
        response,
        '공유를 해제하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      ),
    )
  }
}

/** 건강 공유 해제 */
export const revokeHealthShare = (token: string) =>
  revokeShare('/api/shares/health', token)

/** 제목 공유 해제 */
export const revokeHeadlineShare = (token: string) =>
  revokeShare('/api/shares/headline', token)

/** Fragment Token 기반 공개 URL 생성 */
export const buildShareUrl = (share: CreatedShare) => {
  const type = share.shareType === 'HEALTH' ? 'health' : 'headline'
  return `${window.location.origin}/share/${type}#${share.shareToken}`
}
