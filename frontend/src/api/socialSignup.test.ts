// 소셜 초대 가입 API 검증
import { afterEach, describe, expect, it, vi } from 'vitest'
import { completeSocialSignup } from './socialSignup'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('social signup API', () => {
  it('CSRF Token과 초대 코드로 소셜 가입을 완료한다', async () => {
    document.cookie = 'XSRF-TOKEN=test-social-csrf; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            authenticated: true,
            userId: '42',
            role: 'USER',
            expiresInSeconds: 7200,
          }),
      })
      .mockResolvedValueOnce({ ok: true })
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      completeSocialSignup({
        inviteCode: 'INVITE-2026',
        agreementsAccepted: true,
      }),
    ).resolves.toMatchObject({ authenticated: true, userId: '42' })
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/signup/social', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'test-social-csrf',
      },
      body: JSON.stringify({
        inviteCode: 'INVITE-2026',
        agreementsAccepted: true,
      }),
    })
  })

  it('가입 대기 Session 만료를 안전한 안내로 변환한다', async () => {
    document.cookie = 'XSRF-TOKEN=test-social-csrf; path=/'
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce({ ok: true })
        .mockResolvedValueOnce({
          ok: false,
          json: () => Promise.resolve({ code: 'SOCIAL_SIGNUP_NOT_FOUND' }),
        }),
    )

    await expect(
      completeSocialSignup({ inviteCode: 'INVITE-2026', agreementsAccepted: true }),
    ).rejects.toThrow('소셜 인증 정보가 만료되었습니다. 다시 로그인해 주세요.')
  })

  it('가입 직전 기존 이메일 충돌을 기존 로그인 안내로 변환한다', async () => {
    document.cookie = 'XSRF-TOKEN=test-social-csrf; path=/'
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce({ ok: true })
        .mockResolvedValueOnce({
          ok: false,
          json: () =>
            Promise.resolve({ code: 'SOCIAL_EMAIL_ALREADY_REGISTERED' }),
        }),
    )

    await expect(
      completeSocialSignup({
        inviteCode: 'INVITE-2026',
        agreementsAccepted: true,
      }),
    ).rejects.toThrow(
      '이미 가입된 이메일입니다. 기존 로그인 방식을 이용해 주세요.',
    )
  })
})
