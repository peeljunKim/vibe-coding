// 저장 기록 삭제와 재분석 상호작용 검증
import { useState } from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SavedRecordsPage from './SavedRecordsPage'

const record = {
  id: '31',
  articleUrl: 'https://news.example/article',
  title: '저장된 건강 기사',
  overallStatus: 'CAUTION' as const,
  analyzedAt: '2026-09-24T01:00:00Z',
  expiresAt: '2026-10-24T01:00:00Z',
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('SavedRecordsPage', () => {
  it('부모가 새 저장 기록 목록을 전달하면 화면을 갱신한다', () => {
    const { rerender } = render(
      <MemoryRouter>
        <SavedRecordsPage records={[record]} />
      </MemoryRouter>,
    )

    rerender(
      <MemoryRouter>
        <SavedRecordsPage
          records={[{
            ...record,
            id: '32',
            title: '새로 전달된 건강 기사',
          }]}
        />
      </MemoryRouter>,
    )

    expect(
      screen.getByRole('heading', { name: '새로 전달된 건강 기사' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: '저장된 건강 기사' }),
    ).not.toBeInTheDocument()
  })

  it('확인 후 저장 기록을 삭제하고 목록에서 제거한다', async () => {
    const onDelete = vi.fn().mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    function ControlledRecordsPage() {
      const [records, setRecords] = useState([record])
      return (
        <SavedRecordsPage
          records={records}
          onDelete={async (recordId) => {
            await onDelete(recordId)
            setRecords((current) =>
              current.filter((item) => item.id !== recordId),
            )
          }}
        />
      )
    }

    render(
      <MemoryRouter>
        <ControlledRecordsPage />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: '삭제' }))

    await waitFor(() => expect(onDelete).toHaveBeenCalledWith('31'))
    expect(screen.getByText('저장한 건강 뉴스가 없습니다.')).toBeInTheDocument()
  })

  it('확인 후 기사 URL과 기록 ID로 재분석을 요청한다', async () => {
    const onReanalyze = vi.fn().mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    render(
      <MemoryRouter>
        <SavedRecordsPage records={[record]} onReanalyze={onReanalyze} />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: '다시 분석' }))

    await waitFor(() => expect(onReanalyze).toHaveBeenCalledWith(record))
  })

  it('전체 삭제 실패 시 기존 목록과 오류를 유지한다', async () => {
    const onDeleteAll = vi.fn().mockRejectedValue(new Error('삭제 실패'))
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    render(
      <MemoryRouter>
        <SavedRecordsPage records={[record]} onDeleteAll={onDeleteAll} />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: '전체 기록 삭제' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('삭제 실패')
    expect(screen.getByRole('heading', { name: '저장된 건강 기사' })).toBeInTheDocument()
  })
})
