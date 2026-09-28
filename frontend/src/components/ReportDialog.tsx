// 분석 결과 문제 신고 입력창
import { useState } from 'react'
import type { ReportType } from '../api/reports'

interface ReportDialogProps {
  onClose: () => void
  onSubmit: (reportType: ReportType, description: string) => Promise<void>
}

const reportTypes: Array<{ value: ReportType; label: string }> = [
  { value: 'WRONG_JUDGMENT', label: '판정이 잘못된 것 같음' },
  {
    value: 'IRRELEVANT_EVIDENCE',
    label: '근거가 관련 없거나 신뢰하기 어려움',
  },
  { value: 'BROKEN_EVIDENCE_LINK', label: '근거 링크가 열리지 않음' },
  { value: 'INACCURATE_HEADLINE', label: '제목 분석이 부정확함' },
  { value: 'UI_OR_FUNCTION_ERROR', label: '화면 또는 기능 오류' },
]

function ReportDialog({ onClose, onSubmit }: ReportDialogProps) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  return (
    <div className="report-dialog-backdrop" role="presentation">
      <section
        className="report-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="report-dialog-heading"
      >
        <h2 id="report-dialog-heading">문제 신고</h2>
        <p>신고 당시 분석 결과와 근거 링크가 함께 저장됩니다.</p>
        <form
          onSubmit={(event) => {
            event.preventDefault()
            const form = new FormData(event.currentTarget)
            const reportType = form.get('reportType') as ReportType
            const descriptionValue = form.get('description')
            const description =
              typeof descriptionValue === 'string'
                ? descriptionValue.trim()
                : ''
            const length = Array.from(description).length
            if (length < 1 || length > 2000) {
              setError('설명은 1자 이상 2,000자 이하로 입력해 주세요.')
              return
            }
            setSubmitting(true)
            setError(null)
            void onSubmit(reportType, description)
              .then(onClose)
              .catch((reason: unknown) => {
                setSubmitting(false)
                setError(
                  reason instanceof Error
                    ? reason.message
                    : '신고를 접수하지 못했습니다.',
                )
              })
          }}
        >
          <label htmlFor="report-type">문제 유형</label>
          <select id="report-type" name="reportType" defaultValue="WRONG_JUDGMENT">
            {reportTypes.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>

          <label htmlFor="report-description">간단한 설명</label>
          <textarea
            id="report-description"
            name="description"
            rows={6}
            aria-describedby="report-description-guide"
          />
          <small id="report-description-guide">1자 이상 2,000자 이하</small>
          {error ? <p role="alert">{error}</p> : null}

          <div className="report-dialog__actions">
            <button type="button" onClick={onClose} disabled={submitting}>
              취소
            </button>
            <button className="primary-button" type="submit" disabled={submitting}>
              {submitting ? '접수 중' : '신고 접수'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}

export default ReportDialog
