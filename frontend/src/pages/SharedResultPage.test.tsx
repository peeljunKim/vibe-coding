// 공개 공유 결과 화면 검증
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getSharedHeadlineResult,
  getSharedHealthResult,
} from '../api/shares'
import SharedResultPage from './SharedResultPage'

vi.mock('../api/shares', () => ({
  getSharedHealthResult: vi.fn(),
  getSharedHeadlineResult: vi.fn(),
}))

beforeEach(() => {
  window.location.hash = '#public-share-token'
  vi.mocked(getSharedHealthResult).mockReset()
  vi.mocked(getSharedHeadlineResult).mockReset()
})

afterEach(() => {
  cleanup()
  window.location.hash = ''
})

describe('SharedResultPage', () => {
  it('비로그인 건강 공유 Snapshot을 읽기 전용으로 표시한다', async () => {
    vi.mocked(getSharedHealthResult).mockResolvedValue({
      shareType: 'HEALTH',
      expiresAt: '2026-10-05T01:00:00Z',
      article: {
        url: 'https://news.example/article',
        title: '공유된 건강 기사',
        publisher: '테스트 언론사',
      },
      analyzedAt: '2026-09-28T01:00:00Z',
      overallStatus: 'CAUTION',
      confirmationRate: '50.00',
      confirmedClaimCount: 1,
      totalClaimCount: 2,
      claims: [
        {
          order: 1,
          claim: '공유된 핵심 주장',
          status: 'NEEDS_REVIEW',
          reason: '추가 확인이 필요합니다.',
          evidences: [
            {
              sourceKind: 'OFFICIAL',
              title: '공식 근거',
              provider: '공식기관',
              sourceUrl: 'https://evidence.example/source',
              summary: '공식 근거 요약',
            },
          ],
        },
      ],
      expertReviewStatus: 'NOT_REVIEWED',
      limitedEvidence: true,
    })

    render(<SharedResultPage type="health" />)

    expect(
      await screen.findByRole('heading', { name: '공유된 건강 기사' }),
    ).toBeInTheDocument()
    expect(screen.getByText('공유된 핵심 주장')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '근거 원문 보기 ↗' })).toHaveAttribute(
      'rel',
      'noreferrer',
    )
    expect(screen.getByText('로그인 후 신고할 수 있습니다.')).toBeInTheDocument()
    expect(getSharedHealthResult).toHaveBeenCalledWith('public-share-token')
  })

  it('만료·해제·존재하지 않는 링크를 같은 오류로 안내한다', async () => {
    vi.mocked(getSharedHeadlineResult).mockRejectedValue(
      new Error('공유 결과를 확인할 수 없습니다.'),
    )

    render(<SharedResultPage type="headline" />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '공유 결과를 확인할 수 없습니다.',
    )
  })
})
