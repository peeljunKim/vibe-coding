// 저장 건강 분석 API
export interface HealthRecordSummary {
  id: string
  title: string
  overallStatus: 'RELIABLE' | 'CAUTION' | 'DOUBTFUL'
  analyzedAt: string
  expiresAt: string
}

export interface HealthRecordPage {
  items: HealthRecordSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

interface BackendHealthRecordSummary extends Omit<HealthRecordSummary, 'id'> {
  id: string | number
}

interface BackendHealthRecordPage extends Omit<HealthRecordPage, 'items'> {
  items: BackendHealthRecordSummary[]
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

const readError = async (
  response: Response,
  fallback: string,
  unauthenticated: string,
) => {
  if (response.status === 401) {
    return unauthenticated
  }
  const problem = (await response.json().catch(() => ({}))) as {
    code?: string
  }
  if (problem.code === 'ANALYSIS_NOT_FOUND') {
    return '저장할 분석 결과를 찾을 수 없습니다.'
  }
  if (problem.code === 'ANALYSIS_NOT_COMPLETED') {
    return '분석이 완료된 뒤 저장해 주세요.'
  }
  return fallback
}

const csrfToken = async () => {
  const response = await fetch('/api/csrf', { credentials: 'include' })
  const token = readCookie('XSRF-TOKEN')
  if (!response.ok || !token) {
    throw new Error('저장 요청을 시작하지 못했습니다. 다시 시도해 주세요.')
  }
  return token
}

/** 완료된 건강 분석의 명시적 저장 */
export const saveHealthRecord = async (analysisId: string) => {
  const token = await csrfToken()
  const response = await fetch('/api/health-records', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': token,
    },
    body: JSON.stringify({ analysisId }),
  })
  if (!response.ok) {
    throw new Error(
      await readError(
        response,
        '결과를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.',
        '로그인 후 결과를 저장할 수 있습니다.',
      ),
    )
  }
  const saved = await readJson<BackendHealthRecordSummary>(response)
  return { ...saved, id: String(saved.id) }
}

/** 회원별 최신 저장 기록 조회 */
export const listHealthRecords = async (page = 0, size = 20) => {
  const response = await fetch(
    `/api/health-records?page=${page}&size=${size}`,
    {
      credentials: 'include',
      cache: 'no-store',
    },
  )
  if (!response.ok) {
    throw new Error(
      await readError(
        response,
        '저장 기록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
        '로그인 후 저장 기록을 확인할 수 있습니다.',
      ),
    )
  }
  const pageResult = await readJson<BackendHealthRecordPage>(response)
  return {
    ...pageResult,
    items: pageResult.items.map((item) => ({ ...item, id: String(item.id) })),
  }
}
