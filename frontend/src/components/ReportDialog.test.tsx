// 문제 신고 입력 화면 검증
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ReportDialog from './ReportDialog'

describe('ReportDialog', () => {
  it('문제 유형과 설명을 입력해 신고한다', () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<ReportDialog onClose={vi.fn()} onSubmit={onSubmit} />)

    fireEvent.change(screen.getByLabelText('문제 유형'), {
      target: { value: 'BROKEN_EVIDENCE_LINK' },
    })
    fireEvent.change(screen.getByLabelText('간단한 설명'), {
      target: { value: '두 번째 근거 링크가 열리지 않습니다.' },
    })
    fireEvent.click(screen.getByRole('button', { name: '신고 접수' }))

    expect(onSubmit).toHaveBeenCalledWith(
      'BROKEN_EVIDENCE_LINK',
      '두 번째 근거 링크가 열리지 않습니다.',
    )
  })
})
