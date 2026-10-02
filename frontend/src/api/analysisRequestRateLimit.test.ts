// 분석 접수 요청 제한 API 계약 검증
import { afterEach, expect, it, vi } from 'vitest'
import { analyzeHealthArticle } from './healthAnalysis'
import { analyzeHeadline } from './headlineAnalysis'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

it('건강 분석 429 응답을 재시도 안내로 표시하고 Polling하지 않는다', async () => {
  document.cookie = 'XSRF-TOKEN=analysis-csrf; path=/'
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce({ ok: true })
    .mockResolvedValueOnce({
      ok: false,
      status: 429,
      json: () =>
        Promise.resolve({ code: 'ANALYSIS_REQUEST_RATE_LIMIT_EXCEEDED' }),
    })
  vi.stubGlobal('fetch', fetchMock)

  await expect(
    analyzeHealthArticle('https://news.example/health'),
  ).rejects.toThrow('요청이 너무 많습니다. 1분 후 다시 시도해 주세요.')
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

it('제목 분석 429 응답을 재시도 안내로 표시하고 Polling하지 않는다', async () => {
  document.cookie = 'XSRF-TOKEN=analysis-csrf; path=/'
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce({ ok: true })
    .mockResolvedValueOnce({
      ok: false,
      status: 429,
      json: () =>
        Promise.resolve({ code: 'ANALYSIS_REQUEST_RATE_LIMIT_EXCEEDED' }),
    })
  vi.stubGlobal('fetch', fetchMock)

  await expect(
    analyzeHeadline('https://news.example/headline'),
  ).rejects.toThrow('요청이 너무 많습니다. 1분 후 다시 시도해 주세요.')
  expect(fetchMock).toHaveBeenCalledTimes(2)
})
