// 건강 분석 결과 신고 흐름 검증
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import HealthResultPage from './HealthResultPage'

describe('HealthResultPage report flow', () => {
  it('로그인 회원이 현재 분석 결과를 신고한다', () => {
    const onReport = vi.fn().mockResolvedValue(undefined)
    render(
      <MemoryRouter>
        <HealthResultPage
          authenticated
          data={{
            analysisId: 'health-17',
            article: {
              title: '건강 기사',
              publisher: '테스트 언론사',
              url: 'https://news.example/article',
            },
            analyzedAt: '2026-09-26T01:00:00Z',
            claim: '건강 주장',
            claimStatus: 'NEEDS_REVIEW',
            reasons: ['추가 확인이 필요합니다.'],
            evidences: [],
          }}
          onNewArticle={vi.fn()}
          onReport={onReport}
        />
      </MemoryRouter>,
    )

    fireEvent.click(
      screen.getByRole('button', { name: '문제가 있다면 결과 신고' }),
    )
    fireEvent.change(screen.getByLabelText('간단한 설명'), {
      target: { value: '판정을 다시 확인해 주세요.' },
    })
    fireEvent.click(screen.getByRole('button', { name: '신고 접수' }))

    expect(onReport).toHaveBeenCalledWith(
      'health-17',
      'WRONG_JUDGMENT',
      '판정을 다시 확인해 주세요.',
    )
  })
})
