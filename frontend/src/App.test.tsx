// 데스크톱 화면 전환 검증
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getSession, login, logout } from './api/auth'
import { listHealthRecords, saveHealthRecord } from './api/healthRecords'
import {
  requestRecoveryCode,
  verifyUsernameRecovery,
} from './api/accountRecovery'
import App from './App'
import AdminReportsPage from './pages/AdminReportsPage'
import HealthResultPage from './pages/HealthResultPage'
import type {
  HealthResultViewData,
  ReportSummaryViewData,
} from './types/pageData'

vi.mock('./api/auth', () => ({
  getSession: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('./api/accountRecovery', () => ({
  requestRecoveryCode: vi.fn(),
  resetRecoveredPassword: vi.fn(),
  verifyUsernameRecovery: vi.fn(),
}))

vi.mock('./api/healthRecords', () => ({
  listHealthRecords: vi.fn(),
  saveHealthRecord: vi.fn(),
}))

beforeEach(() => {
  vi.mocked(getSession).mockReset()
  vi.mocked(login).mockReset()
  vi.mocked(logout).mockReset()
  vi.mocked(requestRecoveryCode).mockReset()
  vi.mocked(verifyUsernameRecovery).mockReset()
  vi.mocked(listHealthRecords).mockReset()
  vi.mocked(saveHealthRecord).mockReset()
  vi.mocked(getSession).mockResolvedValue({ authenticated: false })
  vi.mocked(logout).mockResolvedValue(undefined)
  vi.mocked(listHealthRecords).mockResolvedValue({
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
  })
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

const renderApp = (path = '/') =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )

describe('App', () => {
  it('기능 선택 홈을 기본 화면으로 표시한다', () => {
    renderApp()

    expect(
      screen.getByRole('heading', {
        name: '기사의 주장과 제목을 쉽게 확인해 보세요',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '복사한 건강 기사 확인하기' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '복사한 기사 제목 확인하기' }),
    ).toBeInTheDocument()
  })

  it('Backend 지원 상태를 펼치고 키보드로 다시 닫는다', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve([
          { name: 'API 통신사', category: 'NEWS_AGENCY', status: 'ACTIVE' },
          {
            name: 'API 중단사',
            category: 'HEALTH_MEDICAL',
            status: 'TEMPORARILY_DISABLED',
          },
          {
            name: 'API 후보사',
            category: 'GENERAL_NEWSPAPER',
            status: 'UNSUPPORTED',
          },
        ]),
    })
    vi.stubGlobal('fetch', fetchMock)
    renderApp()

    const openButton = screen.getByRole('button', {
      name: '지원 언론사 보기',
    })
    expect(openButton).toHaveAttribute('aria-expanded', 'false')

    fireEvent.click(openButton)

    const closeButton = screen.getByRole('button', { name: '접기 ↑' })
    expect(closeButton).toHaveAttribute('aria-expanded', 'true')
    expect(
      screen.getByRole('heading', { name: '지원 언론사' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('API 통신사')).toBeInTheDocument()
    expect(screen.getByText('API 중단사')).toBeInTheDocument()
    expect(screen.getByText('API 후보사')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/publishers')

    closeButton.focus()
    fireEvent.keyDown(closeButton, { key: 'Escape' })

    const reopenedButton = screen.getByRole('button', {
      name: '지원 언론사 보기',
    })
    expect(reopenedButton).toHaveFocus()
    expect(reopenedButton).toHaveAttribute('aria-expanded', 'false')
    expect(
      screen.queryByRole('heading', { name: '지원 언론사' }),
    ).not.toBeInTheDocument()
  })

  it('지원 언론사 조회 중 상태를 표시한다', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise(() => undefined)),
    )
    renderApp()

    fireEvent.click(screen.getByRole('button', { name: '지원 언론사 보기' }))

    expect(
      screen.getByRole('status', {
        name: '지원 언론사 정보를 불러오는 중입니다.',
      }),
    ).toBeInTheDocument()
  })

  it('지원 언론사 조회 실패를 숨기지 않는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network')))
    renderApp()

    fireEvent.click(screen.getByRole('button', { name: '지원 언론사 보기' }))

    expect(
      await screen.findByRole('alert', {
        name: '지원 언론사 정보를 불러오지 못했습니다.',
      }),
    ).toBeInTheDocument()
  })

  it('지원 언론사 빈 목록 상태를 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve([]),
      }),
    )
    renderApp()

    fireEvent.click(screen.getByRole('button', { name: '지원 언론사 보기' }))

    expect(
      await screen.findByText('현재 등록된 언론사가 없습니다.'),
    ).toBeInTheDocument()
  })

  it('건강 기사 확인을 시작하고 취소하면 홈으로 돌아온다', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        readText: vi
          .fn()
          .mockResolvedValue('https://news.example.com/health-article'),
      },
    })
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise(() => undefined)),
    )
    renderApp()

    fireEvent.click(
      screen.getByRole('button', { name: '복사한 건강 기사 확인하기' }),
    )
    expect(
      await screen.findByRole('heading', {
        name: '기사의 근거를 확인하고 있습니다',
      }),
    ).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '분석 취소' }))
    expect(
      screen.getByRole('heading', {
        name: '기사의 주장과 제목을 쉽게 확인해 보세요',
      }),
    ).toBeInTheDocument()
  })

  it('클립보드 건강 기사 URL의 분석을 완료하고 결과 화면으로 이동한다', async () => {
    const articleUrl = 'https://news.example.com/health-article'
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { readText: vi.fn().mockResolvedValue(articleUrl) },
    })
    document.cookie = 'XSRF-TOKEN=test-csrf-token; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            analysisId: 'health-1',
            deadlineAt: '2099-09-20T00:01:30Z',
            pollAfterSeconds: 0,
            guestAccessToken: 'test-guest-health-token',
          }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            analysisId: 'health-1',
            status: 'PROCESSING',
            stage: 'SEARCHING_EVIDENCE',
            deadlineAt: '2099-09-20T00:01:30Z',
            pollAfterSeconds: 0,
          }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            analysisId: 'health-1',
            status: 'COMPLETED',
            stage: 'COMPLETED',
            result: {
              article: {
                url: articleUrl,
                title: '검증된 건강 기사 제목',
                publisher: '테스트 언론사',
                publishedAt: '2026-09-20T09:00:00+09:00',
              },
              analyzedAt: '2026-09-20T00:00:03Z',
              overallStatus: 'CAUTION',
              confirmationRate: 0,
              confirmedClaimCount: 0,
              totalClaimCount: 1,
              claims: [
                {
                  order: 1,
                  claim: '건강 기사 핵심 주장',
                  status: 'INSUFFICIENT',
                  reason: '확인 가능한 외부 근거가 부족합니다.',
                  evidences: [],
                },
              ],
              expertReviewStatus: 'NOT_REVIEWED',
              limitedEvidence: true,
            },
          }),
      })
    vi.stubGlobal('fetch', fetchMock)
    renderApp()

    fireEvent.click(
      screen.getByRole('button', { name: '복사한 건강 기사 확인하기' }),
    )

    expect(
      await screen.findByRole('heading', { name: '건강 기사 핵심 주장' }),
    ).toBeInTheDocument()
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/analyses/health',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/analyses/health/health-1',
      expect.objectContaining({ credentials: 'include' }),
    )
    const [, healthPollRequest] = fetchMock.mock.calls[2] as unknown as [
      RequestInfo | URL,
      RequestInit | undefined,
    ]
    expect(
      new Headers(healthPollRequest?.headers).get('X-Analysis-Access-Token'),
    ).toBe('test-guest-health-token')
  })

  it.each([
    [
      'ARTICLE_NOT_HEALTH_RELATED',
      '건강·의학·보건 관련 기사로 확인되지 않았습니다.',
    ],
    [
      'ARTICLE_TOPIC_UNCERTAIN',
      '건강·의학·보건 관련 기사인지 확인하기 어렵습니다.',
    ],
  ])(
    '%s 실패에는 내부 상세 대신 안전한 안내를 표시한다',
    async (code, message) => {
      Object.defineProperty(navigator, 'clipboard', {
        configurable: true,
        value: {
          readText: vi
            .fn()
            .mockResolvedValue('https://news.example.com/general-article'),
        },
      })
      document.cookie = 'XSRF-TOKEN=test-csrf-token; path=/'
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValueOnce({ ok: true })
          .mockResolvedValueOnce({
            ok: true,
            json: () =>
              Promise.resolve({
                analysisId: 'health-failed-1',
                deadlineAt: '2099-09-20T00:01:30Z',
                pollAfterSeconds: 0,
                guestAccessToken: 'test-guest-health-token',
              }),
          })
          .mockResolvedValueOnce({
            ok: true,
            json: () =>
              Promise.resolve({
                analysisId: 'health-failed-1',
                status: 'FAILED',
                stage: 'FAILED',
                error: {
                  code,
                  detail: '내부 판별 상세 정보',
                },
              }),
          }),
      )
      renderApp()

      fireEvent.click(
        screen.getByRole('button', { name: '복사한 건강 기사 확인하기' }),
      )

      expect(await screen.findByRole('alert')).toHaveTextContent(message)
      expect(screen.queryByText('내부 판별 상세 정보')).not.toBeInTheDocument()
    },
  )

  it('건강 분석 취소 뒤 늦게 도착한 완료 결과를 폐기한다', async () => {
    const articleUrl = 'https://news.example.com/health-article'
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { readText: vi.fn().mockResolvedValue(articleUrl) },
    })
    document.cookie = 'XSRF-TOKEN=test-csrf-token; path=/'
    let resolvePoll: ((response: unknown) => void) | undefined
    const pollResponse = new Promise((resolve) => {
      resolvePoll = resolve
    })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            analysisId: 'health-cancelled-1',
            deadlineAt: '2099-09-20T00:01:30Z',
            pollAfterSeconds: 0,
            guestAccessToken: 'test-guest-health-token',
          }),
      })
      .mockImplementationOnce(() => pollResponse)
    vi.stubGlobal('fetch', fetchMock)
    renderApp()

    fireEvent.click(
      screen.getByRole('button', { name: '복사한 건강 기사 확인하기' }),
    )
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3))

    fireEvent.click(screen.getByRole('button', { name: '분석 취소' }))
    resolvePoll?.({
      ok: true,
      json: () =>
        Promise.resolve({
          analysisId: 'health-cancelled-1',
          status: 'COMPLETED',
          stage: 'COMPLETED',
          result: {
            article: {
              url: articleUrl,
              title: '취소 뒤 도착한 기사',
              publisher: '테스트 언론사',
            },
            analyzedAt: '2026-09-20T00:00:03Z',
            claims: [
              {
                order: 1,
                claim: '폐기되어야 할 건강 기사 주장',
                status: 'INSUFFICIENT',
                reason: '확인 가능한 외부 근거가 부족합니다.',
                evidences: [],
              },
            ],
          },
        }),
    })
    await new Promise((resolve) => window.setTimeout(resolve, 0))

    await waitFor(() =>
      expect(
        screen.getByRole('heading', {
          name: '기사의 주장과 제목을 쉽게 확인해 보세요',
        }),
      ).toBeInTheDocument(),
    )
    expect(
      screen.queryByRole('heading', {
        name: '폐기되어야 할 건강 기사 주장',
      }),
    ).not.toBeInTheDocument()
  })

  it('클립보드 기사 URL의 제목 분석을 완료하고 결과 화면으로 이동한다', async () => {
    const articleUrl = 'https://news.example.com/article'
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { readText: vi.fn().mockResolvedValue(articleUrl) },
    })
    document.cookie = 'XSRF-TOKEN=test-csrf-token; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            analysisId: 'headline-1',
            pollAfterSeconds: 0,
            guestAccessToken: 'test-guest-job-token',
          }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            analysisId: 'headline-1',
            status: 'COMPLETED',
            stage: 'COMPLETED',
            result: {
              article: {
                url: articleUrl,
                title: '검증된 기사 제목',
                publisher: '테스트 언론사',
                publishedAt: '2026-09-20T09:00:00+09:00',
              },
              analyzedAt: '2026-09-20T00:00:03Z',
              issues: [
                {
                  type: 'NO_ISSUE',
                  explanation: '제목과 본문의 핵심 내용이 일치합니다.',
                },
              ],
            },
          }),
      })
    vi.stubGlobal('fetch', fetchMock)
    renderApp()

    fireEvent.click(
      screen.getByRole('button', { name: '복사한 기사 제목 확인하기' }),
    )

    expect(
      await screen.findByRole('heading', { name: '검증된 기사 제목' }),
    ).toBeInTheDocument()
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/csrf',
      expect.objectContaining({ credentials: 'include' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/analyses/headline',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/analyses/headline/headline-1',
      expect.objectContaining({ credentials: 'include' }),
    )
    const [, headlinePollRequest] = fetchMock.mock.calls[2] as unknown as [
      RequestInfo | URL,
      RequestInit | undefined,
    ]
    expect(
      new Headers(headlinePollRequest?.headers).get('X-Analysis-Access-Token'),
    ).toBe('test-guest-job-token')
  })

  it('로그인 Route에 일반 로그인 입력과 초대 안내를 표시한다', () => {
    renderApp('/login')

    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
    expect(screen.getByLabelText('아이디')).toBeInTheDocument()
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument()
    expect(
      screen.getByRole('checkbox', { name: '로그인 상태 유지' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '초대 코드로 회원가입 시작하기' }),
    ).toBeInTheDocument()
  })

  it('Provider별 OAuth 로그인 링크를 표시한다', () => {
    renderApp('/login')

    expect(screen.getByRole('link', { name: '카카오 로그인' })).toHaveAttribute(
      'href',
      expect.stringContaining('/oauth2/authorization/kakao'),
    )
    expect(
      screen.getByRole('link', { name: 'Google 계정으로 로그인' }),
    ).toHaveAttribute(
      'href',
      expect.stringContaining('/oauth2/authorization/google'),
    )
    expect(screen.getByRole('link', { name: '네이버 로그인' })).toHaveAttribute(
      'href',
      expect.stringContaining('/oauth2/authorization/naver'),
    )
  })

  it('일반 로그인 성공 후 홈에서 로그아웃한다', async () => {
    vi.mocked(login).mockResolvedValue({
      authenticated: true,
      userId: '42',
      username: 'health26',
      role: 'USER',
      expiresInSeconds: 604800,
    })
    renderApp('/login')

    fireEvent.change(screen.getByLabelText('아이디'), {
      target: { value: 'health26' },
    })
    fireEvent.change(screen.getByLabelText('비밀번호'), {
      target: { value: 'test-Password23!' },
    })
    fireEvent.click(screen.getByRole('checkbox', { name: '로그인 상태 유지' }))
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(
      await screen.findByRole('button', { name: '로그아웃' }),
    ).toBeInTheDocument()
    expect(login).toHaveBeenCalledWith({
      username: 'health26',
      password: 'test-Password23!',
      rememberMe: true,
    })

    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    expect(
      await screen.findByRole('link', { name: '로그인' }),
    ).toBeInTheDocument()
    expect(logout).toHaveBeenCalledOnce()
  })

  it('로그인 화면에서 아이디 찾기와 인증 결과를 표시한다', async () => {
    vi.mocked(requestRecoveryCode).mockResolvedValue({
      remainingAttempts: 5,
      resendAvailableInSeconds: 60,
    })
    vi.mocked(verifyUsernameRecovery).mockResolvedValue({
      maskedUsername: 'he***********',
    })
    renderApp('/login')

    fireEvent.click(
      screen.getByRole('button', { name: '아이디·비밀번호 찾기' }),
    )
    fireEvent.change(screen.getByLabelText('가입 이메일'), {
      target: { value: 'user@example.com' },
    })
    fireEvent.click(screen.getByRole('button', { name: '인증번호 받기' }))

    expect(
      await screen.findByText(
        '입력한 이메일과 일치하는 계정이 있으면 인증번호를 보냈습니다.',
      ),
    ).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('6자리 인증번호'), {
      target: { value: '482916' },
    })
    fireEvent.click(screen.getByRole('button', { name: '아이디 확인' }))

    expect(await screen.findByText('he***********')).toBeInTheDocument()
    expect(verifyUsernameRecovery).toHaveBeenCalledWith(
      'user@example.com',
      '482916',
    )
  })

  it('기존 인증 Session을 새로고침 뒤 복원한다', async () => {
    vi.mocked(getSession).mockResolvedValue({
      authenticated: true,
      userId: '42',
      role: 'USER',
      expiresInSeconds: 7200,
    })

    renderApp()

    expect(
      await screen.findByRole('button', { name: '로그아웃' }),
    ).toBeInTheDocument()
  })

  it('지연된 초기 Session 조회가 로그인 결과를 덮어쓰지 않는다', async () => {
    let resolveInitialSession!: (session: { authenticated: boolean }) => void
    vi.mocked(getSession).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveInitialSession = resolve
        }),
    )
    vi.mocked(login).mockResolvedValue({
      authenticated: true,
      userId: '42',
      username: 'health26',
      role: 'USER',
      expiresInSeconds: 7200,
    })
    renderApp('/login')

    fireEvent.change(screen.getByLabelText('아이디'), {
      target: { value: 'health26' },
    })
    fireEvent.change(screen.getByLabelText('비밀번호'), {
      target: { value: 'test-Password23!' },
    })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))
    expect(
      await screen.findByRole('button', { name: '로그아웃' }),
    ).toBeInTheDocument()

    act(() => {
      resolveInitialSession({ authenticated: false })
    })

    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument()
  })

  it('로그아웃 실패 시 인증 상태를 유지하고 오류를 안내한다', async () => {
    vi.mocked(getSession).mockResolvedValue({
      authenticated: true,
      userId: '42',
      role: 'USER',
      expiresInSeconds: 7200,
    })
    vi.mocked(logout).mockRejectedValue(
      new Error('로그아웃하지 못했습니다. 다시 시도해 주세요.'),
    )
    renderApp()

    fireEvent.click(await screen.findByRole('button', { name: '로그아웃' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '로그아웃하지 못했습니다. 다시 시도해 주세요.',
    )
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument()
  })

  it.each([
    ['/results/health', '[핵심 주장 데이터가 필요합니다.]'],
    ['/results/title', '[기사 제목 분석 결과 데이터가 필요합니다.]'],
    ['/saved', '저장한 건강 뉴스'],
    ['/admin/reports', '신고 관리'],
  ])('%s Route에 대상 화면을 표시한다', (path, heading) => {
    renderApp(path)

    expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument()
  })

  it('주입된 건강 분석 결과와 근거 출처를 표시한다', () => {
    const result: HealthResultViewData = {
      analysisId: 'analysis-1',
      article: {
        title: '건강 기사 제목',
        publisher: '테스트 언론사',
        url: 'https://news.example.com/article',
        publishedAt: '2026.09.09',
      },
      analyzedAt: '2026.09.09',
      claim: '충분히 긴 건강 기사 핵심 주장도 카드 너비 안에서 표시됩니다.',
      claimStatus: 'NEEDS_REVIEW',
      reasons: ['첫 번째 판정 이유', '두 번째 판정 이유'],
      evidences: [
        {
          id: 'evidence-1',
          title: '근거 자료 제목',
          provider: '공식 기관',
          sourceUrl: 'https://evidence.example.com/source',
          summary: '근거 요약',
          sourceType: '공식 자료',
        },
      ],
    }

    render(
      <MemoryRouter>
        <HealthResultPage data={result} onNewArticle={() => undefined} />
      </MemoryRouter>,
    )

    expect(
      screen.getByRole('heading', { name: result.claim }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '원문 보기 ↗' })).toHaveAttribute(
      'href',
      'https://evidence.example.com/source',
    )
  })

  it('건강 분석 결과의 저장 버튼으로 완료 작업을 저장한다', async () => {
    const result: HealthResultViewData = {
      analysisId: 'analysis-1',
      article: {
        title: '건강 기사 제목',
        publisher: '테스트 언론사',
        url: 'https://news.example.com/article',
      },
      analyzedAt: '2026-09-24T01:00:00Z',
      claim: '건강 기사 핵심 주장',
      claimStatus: 'NEEDS_REVIEW',
      reasons: ['근거가 부족합니다.'],
      evidences: [],
    }
    const onSave = vi.fn().mockResolvedValue(undefined)

    render(
      <MemoryRouter>
        <HealthResultPage
          data={result}
          onNewArticle={() => undefined}
          onSave={onSave}
        />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: '결과 저장' }))

    await waitFor(() => expect(onSave).toHaveBeenCalledWith('analysis-1'))
    expect(screen.getByRole('button', { name: '저장 완료' })).toBeDisabled()
  })

  it('건강 분석 결과 저장이 실패하면 다시 시도할 수 있다', async () => {
    const result: HealthResultViewData = {
      analysisId: 'analysis-retry',
      article: {
        title: '건강 기사 제목',
        publisher: '테스트 언론사',
        url: 'https://news.example.com/article',
      },
      analyzedAt: '2026-09-24T01:00:00Z',
      claim: '건강 기사 핵심 주장',
      claimStatus: 'NEEDS_REVIEW',
      reasons: ['근거가 부족합니다.'],
      evidences: [],
    }
    const onSave = vi
      .fn()
      .mockRejectedValueOnce(new Error('결과를 저장하지 못했습니다.'))
      .mockResolvedValueOnce(undefined)

    render(
      <MemoryRouter>
        <HealthResultPage
          data={result}
          onNewArticle={() => undefined}
          onSave={onSave}
        />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: '결과 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '결과를 저장하지 못했습니다.',
    )
    const retryButton = screen.getByRole('button', { name: '결과 저장' })
    expect(retryButton).toBeEnabled()

    fireEvent.click(retryButton)

    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('button', { name: '저장 완료' })).toBeDisabled()
  })

  it('로그인 회원의 실제 저장 기록 API 목록을 표시한다', async () => {
    vi.mocked(getSession).mockResolvedValue({
      authenticated: true,
      userId: '42',
      role: 'USER',
      expiresInSeconds: 7200,
    })
    vi.mocked(listHealthRecords).mockResolvedValue({
      items: [
        {
          id: '31',
          title: '저장된 건강 기사',
          overallStatus: 'CAUTION',
          analyzedAt: '2026-09-24T01:00:00Z',
          expiresAt: '2026-10-24T01:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    })

    renderApp('/saved')

    expect(
      await screen.findByRole('heading', { name: '저장된 건강 기사' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText('분석일 2026.09.24 · 2026.10.24 삭제'),
    ).toBeInTheDocument()
    expect(listHealthRecords).toHaveBeenCalledWith(0, 20)
  })

  it('저장 기록의 전체 개수와 다음 페이지를 표시한다', async () => {
    vi.mocked(getSession).mockResolvedValue({
      authenticated: true,
      userId: '42',
      role: 'USER',
      expiresInSeconds: 7200,
    })
    vi.mocked(listHealthRecords)
      .mockResolvedValueOnce({
        items: [
          {
            id: '31',
            title: '첫 페이지 기록',
            overallStatus: 'CAUTION',
            analyzedAt: '2026-09-24T01:00:00Z',
            expiresAt: '2026-10-24T01:00:00Z',
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
            id: '11',
            title: '둘째 페이지 기록',
            overallStatus: 'RELIABLE',
            analyzedAt: '2026-09-23T01:00:00Z',
            expiresAt: '2026-10-23T01:00:00Z',
          },
        ],
        page: 1,
        size: 20,
        totalElements: 21,
        totalPages: 2,
        hasNext: false,
      })

    renderApp('/saved')

    expect(
      await screen.findByRole('heading', { name: '첫 페이지 기록' }),
    ).toBeInTheDocument()
    expect(screen.getByText('남은 기록 21개')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }))

    expect(
      await screen.findByRole('heading', { name: '둘째 페이지 기록' }),
    ).toBeInTheDocument()
    expect(listHealthRecords).toHaveBeenLastCalledWith(1, 20)
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeEnabled()
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled()
  })

  it('관리자 신고를 선택하면 실제 입력 데이터로 상세 내용을 갱신한다', () => {
    const reports: ReportSummaryViewData[] = [
      {
        id: 'R-017',
        title: '첫 번째 신고',
        status: '확인 중',
        reportedAt: '2026.09.08',
        publisher: '테스트 언론사',
        analysisType: '건강 뉴스 결과',
        description: '첫 번째 신고 설명',
      },
      {
        id: 'R-018',
        title: '근거 링크가 열리지 않음',
        status: '확인 전',
        reportedAt: '2026.09.09',
        publisher: '다른 언론사',
        analysisType: '건강 뉴스 결과',
        description: '두 번째 신고 설명',
      },
    ]

    render(
      <MemoryRouter>
        <AdminReportsPage
          stats={{ waiting: 1, working: 1, complete: 0 }}
          reports={reports}
        />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: /#R-018/ }))

    expect(
      screen.getByRole('heading', {
        name: '#R-018 근거 링크가 열리지 않음',
      }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('처리 상태')).toHaveValue('확인 전')
  })

  it('로그인에서 회원가입 API와 이메일 인증 API를 거쳐 완료 화면으로 이동한다', async () => {
    document.cookie = 'XSRF-TOKEN=signup-test-csrf; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            userId: 42,
            username: 'healthcheck26',
            email: 'user@example.com',
            remainingAttempts: 5,
            resendAvailableInSeconds: 60,
          }),
      })
      .mockResolvedValueOnce({ ok: true })
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            userId: 42,
            username: 'healthcheck26',
            email: 'user@example.com',
          }),
      })
    vi.stubGlobal('fetch', fetchMock)
    renderApp('/login')

    fireEvent.click(
      screen.getByRole('button', { name: '초대 코드로 회원가입 시작하기' }),
    )
    expect(
      screen.getByRole('heading', {
        name: '1. 초대 코드와 계정 정보를 입력해 주세요',
      }),
    ).toBeInTheDocument()

    fireEvent.change(screen.getByRole('textbox', { name: /초대 코드/ }), {
      target: { value: 'CHECK-2026-A7K9' },
    })
    fireEvent.change(screen.getByRole('textbox', { name: /사용자 아이디/ }), {
      target: { value: 'healthcheck26' },
    })
    fireEvent.change(document.querySelector('[name="signup-password"]')!, {
      target: { value: 'test-Password23!' },
    })
    fireEvent.change(
      document.querySelector('[name="signup-password-confirm"]')!,
      { target: { value: 'test-Password23!' } },
    )
    fireEvent.change(screen.getByRole('textbox', { name: /이메일/ }), {
      target: { value: 'user@example.com' },
    })
    const phoneInput = screen.getByRole('textbox', { name: /휴대전화 번호/ })
    fireEvent.input(phoneInput, { target: { value: '01012345678' } })
    expect(phoneInput).toHaveValue('010-1234-5678')
    fireEvent.click(
      screen.getByRole('checkbox', {
        name: '개인정보 처리와 서비스 이용약관에 동의합니다.',
      }),
    )
    fireEvent.click(screen.getByRole('button', { name: '다음: 이메일 인증' }))
    expect(
      await screen.findByRole('heading', {
        name: 'user@example.com으로 6자리 인증번호를 보냈습니다',
      }),
    ).toBeInTheDocument()

    for (let index = 1; index <= 6; index += 1) {
      fireEvent.change(screen.getByLabelText(`인증번호 ${index}번째 자리`), {
        target: { value: String(index) },
      })
    }
    fireEvent.click(screen.getByRole('button', { name: '인증번호 확인' }))
    expect(
      await screen.findByRole('heading', {
        name: '회원가입이 완료되었습니다',
      }),
    ).toBeInTheDocument()
    expect(screen.getByText('healthcheck26')).toBeInTheDocument()
    expect(screen.getByText('user@example.com')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '로그인하러 가기' }))
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/csrf', {
      credentials: 'include',
    })
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/signup',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      4,
      '/api/signup/email-verification',
      expect.objectContaining({ method: 'POST' }),
    )
  })
})
