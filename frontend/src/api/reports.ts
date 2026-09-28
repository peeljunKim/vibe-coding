// 문제 신고와 관리자 처리 API
export type ReportAnalysisType = 'HEALTH' | 'HEADLINE'

export type ReportType =
  | 'WRONG_JUDGMENT'
  | 'IRRELEVANT_EVIDENCE'
  | 'BROKEN_EVIDENCE_LINK'
  | 'INACCURATE_HEADLINE'
  | 'UI_OR_FUNCTION_ERROR'

export type ReportStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED'

export interface CreateReportRequest {
  analysisType: ReportAnalysisType
  analysisId: string
  reportType: ReportType
  description: string
}

export interface CreatedReport {
  id: string
  status: ReportStatus
  createdAt: string
}

export interface ReportSummary {
  id: string
  analysisType: ReportAnalysisType
  reportType: ReportType
  articleTitle: string
  publisherName: string
  status: ReportStatus
  createdAt: string
  updatedAt: string
  completedAt: string | null
  adminReply: string | null
}

export interface ReportPage {
  items: ReportSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export interface ReportDetail extends ReportSummary {
  description: string
  articleUrl: string
  resultSnapshot: Record<string, unknown>
  version: number
}

export interface UpdateAdminReportRequest {
  status: Exclude<ReportStatus, 'OPEN'>
  adminReply: string | null
  version: number
}

interface BackendReportPage extends Omit<ReportPage, 'items'> {
  items: Array<Omit<ReportSummary, 'id'> & { id: string | number }>
}

const readCookie = (name: string) => {
  const prefix = `${name}=`
  const cookie = document.cookie
    .split(';')
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix))
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
}

const csrfToken = async () => {
  const response = await fetch('/api/csrf', { credentials: 'include' })
  const token = readCookie('XSRF-TOKEN')
  if (!response.ok || !token) {
    throw new Error('신고 요청을 시작하지 못했습니다. 다시 시도해 주세요.')
  }
  return token
}

/** 완료된 분석 결과 신고 */
export const createReport = async (
  request: CreateReportRequest,
): Promise<CreatedReport> => {
  const token = await csrfToken()
  const response = await fetch('/api/reports', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': token,
    },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    throw new Error(
      response.status === 401
        ? '로그인 후 결과를 신고할 수 있습니다.'
        : '신고를 접수하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    )
  }
  const created = (await response.json()) as CreatedReport & {
    id: string | number
  }
  return { ...created, id: String(created.id) }
}

/** 현재 사용자의 신고 목록 조회 */
export const listMyReports = async (page = 0, size = 20) => {
  const response = await fetch(`/api/reports?page=${page}&size=${size}`, {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    throw new Error(
      response.status === 401
        ? '로그인 후 신고 내역을 확인할 수 있습니다.'
        : '신고 내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
    )
  }
  const result = (await response.json()) as BackendReportPage
  return {
    ...result,
    items: result.items.map((item) => ({ ...item, id: String(item.id) })),
  }
}

/** 관리자 신고 목록 조회 */
export const listAdminReports = async (page = 0, size = 20) => {
  const response = await fetch(
    `/api/admin/reports?page=${page}&size=${size}`,
    { credentials: 'include', cache: 'no-store' },
  )
  if (!response.ok) {
    throw new Error(
      response.status === 403
        ? '관리자만 신고를 확인할 수 있습니다.'
        : '신고 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
    )
  }
  const result = (await response.json()) as BackendReportPage
  return {
    ...result,
    items: result.items.map((item) => ({ ...item, id: String(item.id) })),
  }
}

/** 관리자 신고 상세 조회 */
export const getAdminReport = async (reportId: string) => {
  const response = await fetch(
    `/api/admin/reports/${encodeURIComponent(reportId)}`,
    { credentials: 'include', cache: 'no-store' },
  )
  if (!response.ok) {
    throw new Error(
      response.status === 403
        ? '관리자만 신고를 확인할 수 있습니다.'
        : '신고 상세를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
    )
  }
  const result = (await response.json()) as Omit<ReportDetail, 'id'> & {
    id: string | number
  }
  return { ...result, id: String(result.id) }
}

/** 관리자 신고 상태와 답변 변경 */
export const updateAdminReport = async (
  reportId: string,
  request: UpdateAdminReportRequest,
) => {
  const token = await csrfToken()
  const response = await fetch(
    `/api/admin/reports/${encodeURIComponent(reportId)}`,
    {
      method: 'PATCH',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': token,
      },
      body: JSON.stringify(request),
    },
  )
  if (!response.ok) {
    throw new Error(
      response.status === 409
        ? '다른 관리자가 먼저 처리했습니다. 최신 상태를 다시 확인해 주세요.'
        : '신고 상태를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    )
  }
  const result = (await response.json()) as Omit<ReportDetail, 'id'> & {
    id: string | number
  }
  return { ...result, id: String(result.id) }
}
