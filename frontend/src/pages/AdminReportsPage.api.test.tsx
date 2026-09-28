// 관리자 신고 API 연결 검증
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getAdminReport,
  listAdminReports,
  updateAdminReport,
} from '../api/reports'
import AdminReportsPage from './AdminReportsPage'

vi.mock('../api/reports', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/reports')>()
  return {
    ...actual,
    getAdminReport: vi.fn(),
    listAdminReports: vi.fn(),
    updateAdminReport: vi.fn(),
  }
})

beforeEach(() => {
  vi.mocked(listAdminReports).mockReset()
  vi.mocked(getAdminReport).mockReset()
  vi.mocked(updateAdminReport).mockReset()
  vi.mocked(listAdminReports).mockResolvedValue({
    items: [
      {
        id: '17',
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
  })
  vi.mocked(getAdminReport).mockResolvedValue({
    id: '17',
    analysisType: 'HEALTH',
    reportType: 'WRONG_JUDGMENT',
    articleTitle: '건강 기사 제목',
    publisherName: '테스트 언론사',
    status: 'OPEN',
    createdAt: '2026-09-26T01:00:00Z',
    updatedAt: '2026-09-26T01:00:00Z',
    completedAt: null,
    adminReply: null,
    description: '판정을 다시 확인해 주세요.',
    articleUrl: 'https://news.example/article',
    resultSnapshot: {
      schemaVersion: 1,
      analysisType: 'HEALTH',
      result: {
        claims: [
          {
            claim: '비타민 D는 감기를 예방한다',
            status: 'NEEDS_REVIEW',
            reason: '근거가 일관되지 않음',
            evidences: [
              {
                title: '공식 안내',
                provider: '질병관리청',
                sourceUrl: 'https://evidence.example/guide',
                summary: '예방 효과를 단정할 수 없음',
              },
            ],
          },
        ],
      },
    },
    version: 0,
  })
  vi.mocked(updateAdminReport).mockResolvedValue({
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
    version: 1,
  })
})

afterEach(cleanup)

describe('AdminReportsPage API', () => {
  it('관리자 신고 목록과 선택한 상세를 불러온다', async () => {
    render(
      <MemoryRouter>
        <AdminReportsPage />
      </MemoryRouter>,
    )

    expect(await screen.findByText('판정이 잘못된 것 같음')).toBeInTheDocument()
    expect(
      await screen.findByText('판정을 다시 확인해 주세요.'),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: '신고 당시 기사 열기' }),
    ).toHaveAttribute('href', 'https://news.example/article')
    expect(screen.getByText('비타민 D는 감기를 예방한다')).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: '공식 안내 원문 보기' }),
    ).toHaveAttribute('href', 'https://evidence.example/guide')
    await waitFor(() => expect(getAdminReport).toHaveBeenCalledWith('17'))
  })

  it('같은 신고의 상세 요청을 렌더링마다 다시 시작하지 않는다', async () => {
    vi.mocked(getAdminReport).mockReturnValue(new Promise(() => {}))
    const { rerender } = render(
      <MemoryRouter>
        <AdminReportsPage />
      </MemoryRouter>,
    )

    await screen.findByText('#17')
    await waitFor(() => expect(getAdminReport).toHaveBeenCalledTimes(1))

    rerender(
      <MemoryRouter>
        <AdminReportsPage />
      </MemoryRouter>,
    )

    expect(getAdminReport).toHaveBeenCalledTimes(1)
  })

  it('제목 분석 Snapshot의 문제와 대체 제목을 표시한다', async () => {
    vi.mocked(listAdminReports).mockResolvedValue({
      items: [
        {
          id: '18',
          analysisType: 'HEADLINE',
          reportType: 'INACCURATE_HEADLINE',
          articleTitle: '기적의 식품',
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
    })
    vi.mocked(getAdminReport).mockResolvedValue({
      id: '18',
      analysisType: 'HEADLINE',
      reportType: 'INACCURATE_HEADLINE',
      articleTitle: '기적의 식품',
      publisherName: '테스트 언론사',
      status: 'OPEN',
      createdAt: '2026-09-26T01:00:00Z',
      updatedAt: '2026-09-26T01:00:00Z',
      completedAt: null,
      adminReply: null,
      description: '제목을 다시 확인해 주세요.',
      articleUrl: 'https://news.example/headline',
      resultSnapshot: {
        schemaVersion: 1,
        analysisType: 'HEADLINE',
        result: {
          issues: [{ type: 'EXAGGERATED', explanation: '과장된 표현' }],
          alternativeHeadline: '식품 섭취와 암 위험의 연관성',
        },
      },
      version: 0,
    })

    render(
      <MemoryRouter>
        <AdminReportsPage />
      </MemoryRouter>,
    )

    expect(await screen.findByText('과장된 표현')).toBeInTheDocument()
    expect(screen.getByText('식품 섭취와 암 위험의 연관성')).toBeInTheDocument()
  })

  it('21번째 이후 신고로 페이지를 이동한다', async () => {
    vi.mocked(listAdminReports)
      .mockResolvedValueOnce({
        items: [
          {
            id: '17',
            analysisType: 'HEALTH',
            reportType: 'WRONG_JUDGMENT',
            articleTitle: '첫 페이지 기사',
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
        totalElements: 21,
        totalPages: 2,
        hasNext: true,
      })
      .mockResolvedValueOnce({
        items: [
          {
            id: '37',
            analysisType: 'HEADLINE',
            reportType: 'INACCURATE_HEADLINE',
            articleTitle: '21번째 신고',
            publisherName: '테스트 언론사',
            status: 'OPEN',
            createdAt: '2026-09-25T01:00:00Z',
            updatedAt: '2026-09-25T01:00:00Z',
            completedAt: null,
            adminReply: null,
          },
        ],
        page: 1,
        size: 20,
        totalElements: 21,
        totalPages: 2,
        hasNext: false,
      })

    render(
      <MemoryRouter>
        <AdminReportsPage />
      </MemoryRouter>,
    )

    await screen.findByText('#17')
    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }))

    expect(await screen.findByText('#37')).toBeInTheDocument()
    expect(listAdminReports).toHaveBeenLastCalledWith(1, 20)
  })

  it('관리자가 Version과 함께 처리 완료 상태와 답변을 저장한다', async () => {
    render(
      <MemoryRouter>
        <AdminReportsPage />
      </MemoryRouter>,
    )
    await screen.findByText('판정을 다시 확인해 주세요.')

    fireEvent.change(screen.getByLabelText('처리 상태'), {
      target: { value: '처리 완료' },
    })
    fireEvent.change(screen.getByLabelText('관리자 답변'), {
      target: { value: '신고 내용을 확인했습니다.' },
    })
    fireEvent.click(screen.getByRole('button', { name: '상태와 답변 저장' }))

    await waitFor(() =>
      expect(updateAdminReport).toHaveBeenCalledWith('17', {
        status: 'RESOLVED',
        adminReply: '신고 내용을 확인했습니다.',
        version: 0,
      }),
    )
    const stats = screen.getByRole('region', { name: '신고 처리 현황' })
    expect(
      within(within(stats).getByText('확인 전').closest('article')!).getByText(
        '0건',
      ),
    ).toBeInTheDocument()
    expect(
      within(
        within(stats).getByText('처리 완료').closest('article')!,
      ).getByText('1건'),
    ).toBeInTheDocument()
  })

  it('상태 변경 충돌 후 최신 상세를 다시 불러와 재시도한다', async () => {
    const initialDetail = {
      id: '17',
      analysisType: 'HEALTH' as const,
      reportType: 'WRONG_JUDGMENT' as const,
      articleTitle: '건강 기사 제목',
      publisherName: '테스트 언론사',
      status: 'OPEN' as const,
      createdAt: '2026-09-26T01:00:00Z',
      updatedAt: '2026-09-26T01:00:00Z',
      completedAt: null,
      adminReply: null,
      description: '판정을 다시 확인해 주세요.',
      articleUrl: 'https://news.example/article',
      resultSnapshot: { schemaVersion: 1 },
      version: 0,
    }
    vi.mocked(getAdminReport)
      .mockResolvedValueOnce(initialDetail)
      .mockResolvedValueOnce({
        ...initialDetail,
        status: 'IN_PROGRESS',
        updatedAt: '2026-09-26T02:00:00Z',
        version: 1,
      })
    vi.mocked(updateAdminReport).mockRejectedValueOnce(
      new Error(
        '다른 관리자가 먼저 처리했습니다. 최신 상태를 다시 확인해 주세요.',
      ),
    )

    render(
      <MemoryRouter>
        <AdminReportsPage />
      </MemoryRouter>,
    )
    await screen.findByText('판정을 다시 확인해 주세요.')
    fireEvent.change(screen.getByLabelText('처리 상태'), {
      target: { value: '처리 완료' },
    })
    fireEvent.change(screen.getByLabelText('관리자 답변'), {
      target: { value: '신고 내용을 확인했습니다.' },
    })
    fireEvent.click(screen.getByRole('button', { name: '상태와 답변 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '다른 관리자가 먼저 처리했습니다.',
    )
    await waitFor(() => expect(getAdminReport).toHaveBeenCalledTimes(2))
    fireEvent.click(screen.getByRole('button', { name: '상태와 답변 저장' }))

    await waitFor(() =>
      expect(updateAdminReport).toHaveBeenLastCalledWith('17', {
        status: 'RESOLVED',
        adminReply: '신고 내용을 확인했습니다.',
        version: 1,
      }),
    )
  })
})
