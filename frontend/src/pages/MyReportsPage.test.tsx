// 내 신고 내역 화면 검증
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import MyReportsPage from './MyReportsPage'

describe('MyReportsPage', () => {
  it('신고 상태와 관리자 답변을 표시한다', () => {
    render(
      <MemoryRouter>
        <MyReportsPage
          reports={[
            {
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
            },
          ]}
        />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '내 신고 내역' })).toBeInTheDocument()
    expect(screen.getByText('처리 완료')).toBeInTheDocument()
    expect(screen.getByText('신고 내용을 확인했습니다.')).toBeInTheDocument()
  })
})
