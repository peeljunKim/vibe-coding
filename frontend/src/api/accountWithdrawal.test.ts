// 회원 탈퇴 신청 API 검증
import { afterEach, describe, expect, it, vi } from 'vitest'
import { requestAccountWithdrawal } from './accountWithdrawal'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('account withdrawal API', () => {
  it('CSRF Token과 Session Cookie로 탈퇴를 신청한다', async () => {
    document.cookie = 'XSRF-TOKEN=test-withdrawal-csrf; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            recoveryDeadline: '2026-10-06T00:00:00Z',
            scheduledDeletionAt: '2026-11-05T00:00:00Z',
          }),
      })
    vi.stubGlobal('fetch', fetchMock)

    const result = await requestAccountWithdrawal()

    expect(result.scheduledDeletionAt).toBe('2026-11-05T00:00:00Z')
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/account/withdrawal', {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-XSRF-TOKEN': 'test-withdrawal-csrf' },
    })
  })
})
