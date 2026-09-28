// 문제 신고 입력 화면 검증
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ReportDialog from './ReportDialog'

afterEach(cleanup)

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

  it('열릴 때 포커스를 옮기고 Escape로 닫은 뒤 기존 위치로 복귀한다', () => {
    const trigger = document.createElement('button')
    trigger.textContent = '문제 신고'
    document.body.append(trigger)
    trigger.focus()
    const onClose = vi.fn()
    const { unmount } = render(
      <ReportDialog onClose={onClose} onSubmit={vi.fn()} />,
    )
    const reportType = screen.getByLabelText('문제 유형')

    expect(reportType).toHaveFocus()
    fireEvent.keyDown(reportType, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledOnce()

    unmount()
    expect(trigger).toHaveFocus()
    trigger.remove()
  })
})
