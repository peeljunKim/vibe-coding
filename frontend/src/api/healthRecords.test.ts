// 저장 건강 분석 API 검증
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  deleteAllHealthRecords,
  deleteHealthRecord,
  listHealthRecords,
  replaceHealthRecord,
  saveHealthRecord,
} from './healthRecords'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('health records API', () => {
  it('CSRF Token으로 완료된 건강 분석을 저장한다', async () => {
    document.cookie = 'XSRF-TOKEN=record-csrf; path=/'
    const saved = {
      id: '31',
      articleUrl: 'https://news.example/article',
      title: '건강 기사 제목',
      overallStatus: 'CAUTION',
      analyzedAt: '2026-09-24T01:00:00Z',
      expiresAt: '2026-10-24T01:00:00Z',
    }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(saved) })
    vi.stubGlobal('fetch', fetchMock)

    await expect(saveHealthRecord('analysis-1')).resolves.toEqual(saved)
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/health-records',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify({ analysisId: 'analysis-1' }),
      }),
    )
  })

  it('CSRF Token으로 저장 기록을 개별·전체 삭제한다', async () => {
    document.cookie = 'XSRF-TOKEN=record-csrf; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({ ok: true, status: 204 })
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({ ok: true, status: 204 })
    vi.stubGlobal('fetch', fetchMock)

    await deleteHealthRecord('31')
    await deleteAllHealthRecords()

    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/health-records/31',
      expect.objectContaining({ method: 'DELETE' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      4,
      '/api/health-records',
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('완료된 새 분석으로 기존 저장 기록을 교체한다', async () => {
    document.cookie = 'XSRF-TOKEN=record-csrf; path=/'
    const replaced = {
      id: 32,
      articleUrl: 'https://news.example/article',
      title: '새 건강 기사 제목',
      overallStatus: 'RELIABLE',
      analyzedAt: '2026-09-25T01:00:00Z',
      expiresAt: '2026-10-25T01:00:00Z',
    }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(replaced),
      })
    vi.stubGlobal('fetch', fetchMock)

    await expect(replaceHealthRecord('31', 'analysis-2')).resolves.toEqual({
      ...replaced,
      id: '32',
    })
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/health-records/31',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ analysisId: 'analysis-2' }),
      }),
    )
  })

  it('최신 저장 기록 첫 페이지를 조회한다', async () => {
    const page = {
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(page),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(listHealthRecords()).resolves.toEqual(page)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/health-records?page=0&size=20',
      {
        credentials: 'include',
        cache: 'no-store',
      },
    )
  })

  it('비로그인 목록 요청에는 조회 목적에 맞는 안내를 반환한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ status: 401, ok: false }),
    )

    await expect(listHealthRecords()).rejects.toThrow(
      '로그인 후 저장 기록을 확인할 수 있습니다.',
    )
  })
})
