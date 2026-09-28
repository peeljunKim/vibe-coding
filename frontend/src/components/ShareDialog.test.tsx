// 공유 대화상자 상호작용 검증
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ShareDialog from './ShareDialog'

afterEach(cleanup)

describe('ShareDialog', () => {
  it('저장 후 공유하고 링크 복사와 해제를 제공한다', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })
    const onCreate = vi.fn().mockResolvedValue({
      shareType: 'HEALTH' as const,
      shareToken: 'test-health-token',
      expiresAt: '2026-10-05T00:00:00Z',
    })
    const onRevoke = vi.fn().mockResolvedValue(undefined)

    render(
      <ShareDialog
        requiresSave
        onClose={vi.fn()}
        onCreate={onCreate}
        onRevoke={onRevoke}
      />,
    )

    expect(
      screen.getByText('공유하려면 먼저 분석 결과를 저장해 주세요.'),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '저장 후 공유' }))

    const urlInput = await screen.findByLabelText('공유 링크')
    expect(urlInput).toHaveValue(
      'http://localhost:3000/share/health#test-health-token',
    )
    fireEvent.click(screen.getByRole('button', { name: '링크 복사' }))
    await waitFor(() =>
      expect(writeText).toHaveBeenCalledWith(
        'http://localhost:3000/share/health#test-health-token',
      ),
    )

    fireEvent.click(screen.getByRole('button', { name: '공유 해제' }))
    await waitFor(() =>
      expect(onRevoke).toHaveBeenCalledWith('test-health-token'),
    )
    expect(screen.getByRole('status')).toHaveTextContent(
      '공유가 해제되었습니다.',
    )
  })

  it('Clipboard 실패 시 선택 가능한 링크를 유지한다', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockRejectedValue(new Error('denied')),
      },
    })
    render(
      <ShareDialog
        onClose={vi.fn()}
        onCreate={vi.fn().mockResolvedValue({
          shareType: 'HEADLINE',
          shareToken: 'test-headline-token',
          expiresAt: '2026-10-05T00:00:00Z',
        })}
        onRevoke={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '공유 링크 만들기' }))
    const urlInput = await screen.findByLabelText('공유 링크')
    fireEvent.click(screen.getByRole('button', { name: '링크 복사' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '링크를 직접 선택해 복사해 주세요.',
    )
    expect(urlInput).toHaveAttribute('readonly')
  })

  it('링크 생성 후 입력란으로 초점을 옮기고 Escape로 닫는다', async () => {
    const onClose = vi.fn()
    render(
      <ShareDialog
        onClose={onClose}
        onCreate={vi.fn().mockResolvedValue({
          shareType: 'HEALTH',
          shareToken: 'test-focus-token',
          expiresAt: '2026-10-05T00:00:00Z',
        })}
        onRevoke={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '공유 링크 만들기' }))

    const urlInput = await screen.findByLabelText('공유 링크')
    expect(urlInput).toHaveFocus()
    fireEvent.keyDown(urlInput, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledOnce()
  })
})
