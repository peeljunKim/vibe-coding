// 기사 변경 재분석 API 계약 검증
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ArticleChangedError } from './analysisErrors'
import { analyzeHealthArticle } from './healthAnalysis'
import { analyzeHeadline } from './headlineAnalysis'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('analysis reanalysis API', () => {
  it('ARTICLE_CHANGED 실패를 사용자 확인 가능한 오류로 구분한다', async () => {
    document.cookie = 'XSRF-TOKEN=analysis-csrf; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            analysisId: 'health-changed',
            deadlineAt: '2099-09-30T00:01:30Z',
            pollAfterSeconds: 0,
          }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            status: 'FAILED',
            stage: 'FAILED',
            error: { code: 'ARTICLE_CHANGED' },
          }),
      })
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      analyzeHealthArticle('https://news.example/changed'),
    ).rejects.toBeInstanceOf(ArticleChangedError)
  })

  it('사용자 동의 건강·제목 요청에만 reanalyze 값을 전달한다', async () => {
    document.cookie = 'XSRF-TOKEN=analysis-csrf; path=/'
    const healthResult = {
      article: {
        url: 'https://news.example/health',
        title: '변경된 건강 기사',
        publisher: '테스트 언론사',
      },
      analyzedAt: '2026-09-30T00:00:00Z',
      claims: [
        {
          order: 1,
          claim: '변경된 건강 주장',
          status: 'INSUFFICIENT',
          reason: '근거 부족',
          evidences: [],
        },
      ],
    }
    const headlineResult = {
      article: {
        url: 'https://news.example/headline',
        title: '변경된 기사 제목',
        publisher: '테스트 언론사',
      },
      analyzedAt: '2026-09-30T00:00:00Z',
      issues: [],
    }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            analysisId: 'health-reanalyze',
            deadlineAt: '2099-09-30T00:01:30Z',
            pollAfterSeconds: 0,
          }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({ status: 'COMPLETED', result: healthResult }),
      })
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            analysisId: 'headline-reanalyze',
            deadlineAt: '2099-09-30T00:01:30Z',
            pollAfterSeconds: 0,
          }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({ status: 'COMPLETED', result: headlineResult }),
      })
    vi.stubGlobal('fetch', fetchMock)

    await analyzeHealthArticle('https://news.example/health', {
      reanalyze: true,
    })
    await analyzeHeadline('https://news.example/headline', {
      reanalyze: true,
    })

    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/analyses/health',
      expect.objectContaining({
        body: JSON.stringify({
          articleUrl: 'https://news.example/health',
          reanalyze: true,
        }),
      }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      5,
      '/api/analyses/headline',
      expect.objectContaining({
        body: JSON.stringify({
          articleUrl: 'https://news.example/headline',
          reanalyze: true,
        }),
      }),
    )
  })
})
