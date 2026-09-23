// 일반 로그인과 Session API 검증
import { afterEach, describe, expect, it, vi } from 'vitest'
import { getSession, login, logout } from './auth'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('auth API', () => {
  it('CSRF Token으로 로그인하고 인증 후 Token을 갱신한다', async () => {
    document.cookie = 'XSRF-TOKEN=test-login-csrf; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            authenticated: true,
            userId: '42',
            username: 'health26',
            role: 'USER',
            expiresInSeconds: 7200,
          }),
      })
      .mockResolvedValueOnce({ ok: true })
    vi.stubGlobal('fetch', fetchMock)

    const response = await login({
      username: 'health26',
      password: 'test-Password23!',
      rememberMe: false,
    })

    expect(response.authenticated).toBe(true)
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/auth/login', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'test-login-csrf',
      },
      body: JSON.stringify({
        username: 'health26',
        password: 'test-Password23!',
        rememberMe: false,
      }),
    })
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/csrf', {
      credentials: 'include',
    })
  })

  it('잠금 오류의 사용자 안내를 표시한다', async () => {
    document.cookie = 'XSRF-TOKEN=test-login-csrf; path=/'
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce({ ok: true })
        .mockResolvedValueOnce({
          ok: false,
          json: () => Promise.resolve({ code: 'LOGIN_LOCKED' }),
        }),
    )

    await expect(
      login({
        username: 'health26',
        password: 'test-Password23!',
        rememberMe: false,
      }),
    ).rejects.toThrow('로그인이 일시 제한되었습니다. 30분 후 이용 가능합니다.')
  })

  it('로그인 완료 후 CSRF 갱신 실패와 무관하게 인증 결과를 반환한다', async () => {
    document.cookie = 'XSRF-TOKEN=test-login-csrf; path=/'
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce({ ok: true })
        .mockResolvedValueOnce({
          ok: true,
          json: () =>
            Promise.resolve({
              authenticated: true,
              userId: '42',
              username: 'health26',
              role: 'USER',
            }),
        })
        .mockResolvedValueOnce({ ok: false }),
    )

    await expect(
      login({
        username: 'health26',
        password: 'test-Password23!',
        rememberMe: false,
      }),
    ).resolves.toMatchObject({ authenticated: true, userId: '42' })
  })

  it('현재 Session 조회와 로그아웃을 같은 Cookie 자격으로 요청한다', async () => {
    document.cookie = 'XSRF-TOKEN=test-logout-csrf; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ authenticated: true, userId: '42' }),
      })
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({ ok: true })
    vi.stubGlobal('fetch', fetchMock)

    expect((await getSession()).authenticated).toBe(true)
    await logout()

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/session', {
      credentials: 'include',
      cache: 'no-store',
    })
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/auth/logout',
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/csrf', {
      credentials: 'include',
    })
  })

  it('로그아웃 완료 후 CSRF 갱신 실패와 무관하게 종료한다', async () => {
    document.cookie = 'XSRF-TOKEN=test-logout-csrf; path=/'
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce({ ok: true })
        .mockResolvedValueOnce({ ok: true })
        .mockResolvedValueOnce({ ok: false }),
    )

    await expect(logout()).resolves.toBeUndefined()
  })
})
