// 도움말 화면 안내와 접근성 검증
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import HelpPage from './HelpPage'

describe('HelpPage', () => {
  it('두 분석 기능과 결과 해석 기준을 안내한다', () => {
    render(
      <MemoryRouter>
        <HelpPage />
      </MemoryRouter>,
    )

    expect(
      screen.getByRole('heading', { name: '기사체크를 이렇게 이용하세요' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: '건강·의학 뉴스 확인' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: '기사 제목 확인' }),
    ).toBeInTheDocument()
    expect(screen.getByText(/사실일 확률이나 AI 신뢰 확률이 아닙니다/)).toBeInTheDocument()
    expect(screen.getByText(/의사 또는 약사의 진단을 대신하지 않습니다/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '기사 확인 시작하기' })).toHaveAttribute(
      'href',
      '/',
    )
  })
})
