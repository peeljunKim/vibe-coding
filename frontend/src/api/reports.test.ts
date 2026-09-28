// 문제 신고 API 검증
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createReport,
  getAdminReport,
  listAdminReports,
  listMyReports,
  updateAdminReport,
} from './reports'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

describe('reports API', () => {
  it('CSRF Token으로 완료된 분석 결과를 신고한다', async () => {
    document.cookie = 'XSRF-TOKEN=report-csrf; path=/'
    const created = {
      id: '17',
      status: 'OPEN',
      createdAt: '2026-09-26T01:00:00Z',
    }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(created) })
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      createReport({
        analysisType: 'HEALTH',
        analysisId: 'health-1',
        reportType: 'WRONG_JUDGMENT',
        description: '판정과 근거를 다시 확인해 주세요.',
      }),
    ).resolves.toEqual(created)

    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/reports',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify({
          analysisType: 'HEALTH',
          analysisId: 'health-1',
          reportType: 'WRONG_JUDGMENT',
          description: '판정과 근거를 다시 확인해 주세요.',
        }),
      }),
    )
  })

  it('현재 사용자의 신고만 최신순으로 조회한다', async () => {
    const page = {
      items: [
        {
          id: 17,
          analysisType: 'HEALTH',
          reportType: 'WRONG_JUDGMENT',
          articleTitle: '건강 기사 제목',
          publisherName: '테스트 언론사',
          status: 'OPEN',
          createdAt: '2026-09-26T01:00:00Z',
          updatedAt: '2026-09-26T01:00:00Z',
          completedAt: null,
          adminReply: null,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(page),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(listMyReports()).resolves.toEqual({
      ...page,
      items: [{ ...page.items[0], id: '17' }],
    })
    expect(fetchMock).toHaveBeenCalledWith('/api/reports?page=0&size=20', {
      credentials: 'include',
      cache: 'no-store',
    })
  })

  it('관리자 신고 목록을 조회한다', async () => {
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

    await expect(listAdminReports()).resolves.toEqual(page)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/reports?page=0&size=20',
      { credentials: 'include', cache: 'no-store' },
    )
  })

  it('관리자가 선택한 신고의 상세와 Version을 조회한다', async () => {
    const detail = {
      id: 17,
      analysisType: 'HEALTH',
      reportType: 'WRONG_JUDGMENT',
      articleTitle: '건강 기사 제목',
      publisherName: '테스트 언론사',
      status: 'IN_PROGRESS',
      createdAt: '2026-09-26T01:00:00Z',
      updatedAt: '2026-09-26T02:00:00Z',
      completedAt: null,
      adminReply: null,
      description: '판정을 다시 확인해 주세요.',
      articleUrl: 'https://news.example/article',
      resultSnapshot: { schemaVersion: 1 },
      version: 2,
    }
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(detail),
      }),
    )

    await expect(getAdminReport('17')).resolves.toEqual({
      ...detail,
      id: '17',
    })
    expect(fetch).toHaveBeenCalledWith('/api/admin/reports/17', {
      credentials: 'include',
      cache: 'no-store',
    })
  })

  it('관리자가 Version과 함께 상태와 답변을 변경한다', async () => {
    document.cookie = 'XSRF-TOKEN=admin-report-csrf; path=/'
    const updated = {
      id: '17',
      analysisType: 'HEALTH',
      reportType: 'WRONG_JUDGMENT',
      articleTitle: '건강 기사 제목',
      publisherName: '테스트 언론사',
      status: 'RESOLVED',
      createdAt: '2026-09-26T01:00:00Z',
      updatedAt: '2026-09-26T03:00:00Z',
      completedAt: '2026-09-26T03:00:00Z',
      adminReply: '신고 내용을 확인했습니다.',
      description: '판정을 다시 확인해 주세요.',
      articleUrl: 'https://news.example/article',
      resultSnapshot: { schemaVersion: 1 },
      version: 3,
    }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(updated),
      })
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      updateAdminReport('17', {
        status: 'RESOLVED',
        adminReply: '신고 내용을 확인했습니다.',
        version: 2,
      }),
    ).resolves.toEqual(updated)
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/admin/reports/17',
      expect.objectContaining({
        method: 'PATCH',
        credentials: 'include',
        body: JSON.stringify({
          status: 'RESOLVED',
          adminReply: '신고 내용을 확인했습니다.',
          version: 2,
        }),
      }),
    )
  })
})
