// 기능별 일일 이용량 조회 API
export interface UsageCounterResponse {
  limit: number
  used: number
  remaining: number
}

export interface DailyUsageResponse {
  timezone: string
  resetsAt: string
  health: UsageCounterResponse
  headline: UsageCounterResponse
}

/** 현재 회원 또는 비회원의 기능별 일일 이용량 조회 */
export const getDailyUsage = async (): Promise<DailyUsageResponse> => {
  const response = await fetch('/api/usage', {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    throw new Error('오늘 남은 이용 횟수를 확인하지 못했습니다.')
  }
  return (await response.json()) as DailyUsageResponse
}
