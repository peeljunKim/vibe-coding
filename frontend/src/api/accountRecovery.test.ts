// 계정 복구 API 검증
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  requestRecoveryCode,
  resetRecoveredPassword,
  verifyUsernameRecovery,
} from './accountRecovery'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('account recovery API', () => {
  it('CSRF Token으로 아이디 찾기 인증번호를 요청한다', async () => {
    document.cookie = 'XSRF-TOKEN=recovery-csrf; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        status: 202,
        json: () =>
          Promise.resolve({ remainingAttempts: 5, resendAvailableInSeconds: 60 }),
      })
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      requestRecoveryCode('username', 'user@example.com'),
    ).resolves.toEqual({ remainingAttempts: 5, resendAvailableInSeconds: 60 })
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/auth/recovery/username/code',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify({ email: 'user@example.com' }),
      }),
    )
  })

  it('인증된 마스킹 아이디를 반환한다', async () => {
    document.cookie = 'XSRF-TOKEN=recovery-csrf; path=/'
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce({ ok: true })
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ maskedUsername: 'he***********' }),
        }),
    )

    await expect(
      verifyUsernameRecovery('user@example.com', '482916'),
    ).resolves.toEqual({ maskedUsername: 'he***********' })
  })

  it('비밀번호 재설정의 빈 성공 응답을 처리한다', async () => {
    document.cookie = 'XSRF-TOKEN=recovery-csrf; path=/'
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce({ ok: true })
        .mockResolvedValueOnce({ ok: true, status: 204 }),
    )

    await expect(
      resetRecoveredPassword({
        email: 'user@example.com',
        code: '482916',
        password: 'test-Password23!',
        passwordConfirm: 'test-Password23!',
      }),
    ).resolves.toBeUndefined()
  })
})
