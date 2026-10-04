// 일일 이용량 조회 API 검증
import { afterEach, describe, expect, it, vi } from 'vitest'
import { getDailyUsage } from './usage'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('daily usage API', () => {
  it('회원과 비회원 Cookie 자격으로 기능별 현재 이용량을 조회한다', async () => {
    const response = {
      timezone: 'Asia/Seoul',
      resetsAt: '2026-10-04T15:00:00Z',
      health: { limit: 5, used: 2, remaining: 3 },
      headline: { limit: 10, used: 4, remaining: 6 },
    }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(response),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(getDailyUsage()).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith('/api/usage', {
      credentials: 'include',
      cache: 'no-store',
    })
  })

  it('이용량 조회 실패를 사용자 기능 차단 없는 오류로 반환한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))

    await expect(getDailyUsage()).rejects.toThrow(
      '오늘 남은 이용 횟수를 확인하지 못했습니다.',
    )
  })
})
