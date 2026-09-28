// 기사 제목 결과 신고 흐름 검증
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TitleResultPage from './TitleResultPage'

afterEach(cleanup)

describe('TitleResultPage report flow', () => {
  it('로그인 회원이 현재 제목 분석 결과를 신고한다', () => {
    const onReport = vi.fn().mockResolvedValue(undefined)
    render(
      <MemoryRouter>
        <TitleResultPage
          authenticated
          data={{
            analysisId: 'headline-17',
            article: {
              title: '기사 제목',
              publisher: '테스트 언론사',
              url: 'https://news.example/article',
            },
            analyzedAt: '2026-09-26T01:00:00Z',
            issues: [
              { type: 'EXAGGERATED', explanation: '표현이 과장되었습니다.' },
            ],
            alternativeHeadline: '중립적인 기사 제목',
          }}
          onReport={onReport}
        />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: '문제 신고' }))
    fireEvent.change(screen.getByLabelText('문제 유형'), {
      target: { value: 'INACCURATE_HEADLINE' },
    })
    fireEvent.change(screen.getByLabelText('간단한 설명'), {
      target: { value: '제목 판정을 다시 확인해 주세요.' },
    })
    fireEvent.click(screen.getByRole('button', { name: '신고 접수' }))

    expect(onReport).toHaveBeenCalledWith(
      'headline-17',
      'INACCURATE_HEADLINE',
      '제목 판정을 다시 확인해 주세요.',
    )
  })

  it('로그인 회원이 제목 분석 공유 링크를 생성한다', async () => {
    const onShare = vi.fn().mockResolvedValue({
      shareType: 'HEADLINE' as const,
      shareToken: 'test-headline-share-token',
      expiresAt: '2026-10-03T01:00:00Z',
    })
    render(
      <MemoryRouter>
        <TitleResultPage
          authenticated
          data={{
            analysisId: 'headline-17',
            article: {
              title: '기사 제목',
              publisher: '테스트 언론사',
              url: 'https://news.example/article',
            },
            analyzedAt: '2026-09-26T01:00:00Z',
            issues: [
              { type: 'EXAGGERATED', explanation: '표현이 과장되었습니다.' },
            ],
            alternativeHeadline: '중립적인 기사 제목',
          }}
          onShare={onShare}
          onRevokeShare={vi.fn()}
        />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: '공유' }))
    fireEvent.click(screen.getByRole('button', { name: '공유 링크 만들기' }))

    expect(onShare).toHaveBeenCalledWith('headline-17')
    expect(await screen.findByLabelText('공유 링크')).toHaveValue(
      'http://localhost:3000/share/headline#test-headline-share-token',
    )
  })
})
