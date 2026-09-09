// 데스크톱 화면 전환 검증
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import App from './App'
import AdminReportsPage from './pages/AdminReportsPage'
import HealthResultPage from './pages/HealthResultPage'
import type {
  HealthResultViewData,
  ReportSummaryViewData,
} from './types/pageData'

afterEach(cleanup)

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

  it('건강 기사 확인을 시작하고 취소하면 홈으로 돌아온다', () => {
    renderApp()

    fireEvent.click(
      screen.getByRole('button', { name: '복사한 건강 기사 확인하기' }),
    )
    expect(
      screen.getByRole('heading', {
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

  it('로그인에서 회원가입 완료까지 화면을 이동한다', () => {
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
      target: { value: 'Password!23' },
    })
    fireEvent.change(
      document.querySelector('[name="signup-password-confirm"]')!,
      { target: { value: 'Password!23' } },
    )
    fireEvent.change(screen.getByRole('textbox', { name: /이메일/ }), {
      target: { value: 'user@example.com' },
    })
    fireEvent.change(screen.getByRole('textbox', { name: /휴대전화 번호/ }), {
      target: { value: '010-1234-5678' },
    })
    fireEvent.click(
      screen.getByRole('checkbox', {
        name: '개인정보 처리와 서비스 이용약관에 동의합니다.',
      }),
    )
    fireEvent.click(screen.getByRole('button', { name: '다음: 이메일 인증' }))
    expect(
      screen.getByRole('heading', {
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
      screen.getByRole('heading', { name: '회원가입이 완료되었습니다' }),
    ).toBeInTheDocument()
    expect(screen.getByText('healthcheck26')).toBeInTheDocument()
    expect(screen.getByText('user@example.com')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '로그인하러 가기' }))
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
  })
})
