// 공유 API 요청 계약 검증
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createHeadlineShare,
  createHealthShare,
  getSharedHealthResult,
  revokeHeadlineShare,
} from './shares'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('shares API', () => {
  it('건강 저장 기록의 공유 링크를 생성한다', async () => {
    document.cookie = 'XSRF-TOKEN=share-csrf; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            shareType: 'HEALTH',
            shareToken: 'test-health-token',
            expiresAt: '2026-10-05T00:00:00Z',
          }),
      })
    vi.stubGlobal('fetch', fetchMock)

    await expect(createHealthShare('31')).resolves.toMatchObject({
      shareType: 'HEALTH',
      shareToken: 'test-health-token',
    })
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/csrf', {
      credentials: 'include',
    })
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/health-records/31/shares',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
      }),
    )
  })

  it('공개 건강 공유 결과는 Token Header로 조회한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve({
          shareType: 'HEALTH',
          expiresAt: '2026-10-05T00:00:00Z',
          article: {
            title: '공유 건강 기사',
            publisher: '테스트 언론사',
            url: 'https://news.example/article',
          },
          analyzedAt: '2026-09-28T00:00:00Z',
          overallStatus: 'CAUTION',
          confirmationRate: 50,
          confirmedClaimCount: 1,
          totalClaimCount: 2,
          claims: [],
          expertReviewStatus: 'NOT_REVIEWED',
          limitedEvidence: false,
        }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await getSharedHealthResult('fragment-token')

    expect(fetchMock).toHaveBeenCalledWith('/api/shares/health', {
      cache: 'no-store',
      headers: { 'X-Share-Token': 'fragment-token' },
    })
  })

  it('제목 공유 생성과 해제는 인증과 CSRF를 사용한다', async () => {
    document.cookie = 'XSRF-TOKEN=share-csrf; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            shareType: 'HEADLINE',
            shareToken: 'test-headline-token',
            expiresAt: '2026-10-05T00:00:00Z',
          }),
      })
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({ ok: true })
    vi.stubGlobal('fetch', fetchMock)

    await createHeadlineShare('headline-17')
    await revokeHeadlineShare('test-headline-token')

    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/analyses/headline/headline-17/shares',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      4,
      '/api/shares/headline',
      expect.objectContaining({
        method: 'DELETE',
      }),
    )
    const [, revokeRequest] = fetchMock.mock.calls[3] as unknown as [
      RequestInfo | URL,
      RequestInit,
    ]
    expect(new Headers(revokeRequest.headers).get('X-Share-Token')).toBe(
      'test-headline-token',
    )
  })
})
