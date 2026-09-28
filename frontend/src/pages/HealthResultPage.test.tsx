// 건강 분석 결과 신고 흐름 검증
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import HealthResultPage from './HealthResultPage'

afterEach(cleanup)

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

  it('미저장 결과를 저장한 뒤 공유 링크를 생성한다', async () => {
    const onSave = vi.fn().mockResolvedValue({
      id: '31',
      title: '건강 기사',
      overallStatus: 'CAUTION',
      analyzedAt: '2026-09-26T01:00:00Z',
      expiresAt: '2026-10-26T01:00:00Z',
    })
    const onShare = vi.fn().mockResolvedValue({
      shareType: 'HEALTH' as const,
      shareToken: 'test-health-share-token',
      expiresAt: '2026-10-03T01:00:00Z',
    })
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
          onSave={onSave}
          onShare={onShare}
          onRevokeShare={vi.fn()}
        />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: '결과 공유' }))
    fireEvent.click(screen.getByRole('button', { name: '저장 후 공유' }))

    await waitFor(() => expect(onSave).toHaveBeenCalledWith('health-17'))
    expect(onShare).toHaveBeenCalledWith('31')
    expect(await screen.findByLabelText('공유 링크')).toHaveValue(
      'http://localhost:3000/share/health#test-health-share-token',
    )
  })

  it('비로그인 사용자의 공유 생성을 차단한다', () => {
    render(
      <MemoryRouter>
        <HealthResultPage
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
          onShare={vi.fn()}
          onRevokeShare={vi.fn()}
        />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: '결과 공유' }))

    expect(screen.getByRole('status')).toHaveTextContent(
      '로그인 후 결과를 공유할 수 있습니다.',
    )
  })
})
