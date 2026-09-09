// 관리자 신고 처리 화면
import { useState } from 'react'
import AppHeader from '../components/AppHeader'
import type {
  ReportStatsViewData,
  ReportStatus,
  ReportSummaryViewData,
} from '../types/pageData'

interface AdminReportsPageProps {
  stats?: ReportStatsViewData
  reports?: ReportSummaryViewData[]
  onSubmit?: (reportId: string, status: ReportStatus, answer: string) => void
}

const reportStatuses: ReportStatus[] = ['확인 전', '확인 중', '처리 완료']

function isReportStatus(
  value: FormDataEntryValue | null,
): value is ReportStatus {
  return (
    typeof value === 'string' &&
    reportStatuses.some((status) => status === value)
  )
}

function AdminReportsPage({ stats, reports, onSubmit }: AdminReportsPageProps) {
  const [selectedId, setSelectedId] = useState<string>()
  const selectedReport =
    reports?.find((report) => report.id === selectedId) ?? reports?.[0]

  return (
    <div className="app-page admin-page">
      <AppHeader section="신고 관리자">
        <span>ADMIN</span>
      </AppHeader>

      <main className="admin-content">
        <header className="admin-heading">
          <h1>신고 관리</h1>
          <p>
            상태 변경과 짧은 답변만 작성할 수 있습니다. 분석 결과는 수정할 수
            없습니다.
          </p>
        </header>

        <section className="report-stats" aria-label="신고 처리 현황">
          <article className="report-stat report-stat--waiting">
            <span>확인 전</span>
            <strong>{stats ? `${stats.waiting}건` : '-'}</strong>
          </article>
          <article className="report-stat report-stat--working">
            <span>확인 중</span>
            <strong>{stats ? `${stats.working}건` : '-'}</strong>
          </article>
          <article className="report-stat report-stat--complete">
            <span>처리 완료</span>
            <strong>{stats ? `${stats.complete}건` : '-'}</strong>
          </article>
        </section>

        <div className="report-workspace">
          <section
            className="report-list"
            aria-labelledby="report-list-heading"
          >
            <h2 id="report-list-heading">신고 목록</h2>
            <div>
              {reports?.length ? (
                reports.map((report) => (
                  <button
                    type="button"
                    key={report.id}
                    onClick={() => setSelectedId(report.id)}
                    aria-pressed={selectedReport?.id === report.id}
                  >
                    <span className="report-list__id">#{report.id}</span>
                    <strong>{report.title}</strong>
                    <span
                      className={`report-status report-status--${report.status === '확인 전' ? 'waiting' : report.status === '확인 중' ? 'working' : 'complete'}`}
                    >
                      {report.status}
                    </span>
                    <small>{report.reportedAt}</small>
                  </button>
                ))
              ) : (
                <p className="report-list__empty" role="status">
                  {reports
                    ? '접수된 신고가 없습니다.'
                    : '[신고 통계와 목록 데이터가 필요합니다.]'}
                </p>
              )}
            </div>
          </section>

          <section
            className="report-detail"
            aria-labelledby="report-detail-heading"
          >
            {selectedReport ? (
              <>
                <span className="status-badge status-badge--warning">
                  {selectedReport.status}
                </span>
                <h2 id="report-detail-heading">
                  #{selectedReport.id} {selectedReport.title}
                </h2>
                <p>
                  {selectedReport.publisher} · {selectedReport.analysisType}
                </p>

                <section
                  className="report-description"
                  aria-labelledby="report-description-heading"
                >
                  <h3 id="report-description-heading">사용자 설명</h3>
                  <p>{selectedReport.description}</p>
                </section>

                <form
                  onSubmit={(event) => {
                    event.preventDefault()
                    const formData = new FormData(event.currentTarget)
                    const status = formData.get('status')
                    const answer = formData.get('answer')
                    if (!isReportStatus(status)) {
                      return
                    }
                    onSubmit?.(
                      selectedReport.id,
                      status,
                      typeof answer === 'string' ? answer : '',
                    )
                  }}
                >
                  <label htmlFor="report-status">처리 상태</label>
                  <select
                    id="report-status"
                    name="status"
                    key={selectedReport.id}
                    defaultValue={selectedReport.status}
                  >
                    <option>확인 전</option>
                    <option>확인 중</option>
                    <option>처리 완료</option>
                  </select>
                  <label htmlFor="admin-answer">관리자 답변</label>
                  <textarea
                    id="admin-answer"
                    name="answer"
                    defaultValue={selectedReport.answer ?? ''}
                    placeholder="관리자 답변을 입력하세요"
                  />
                  <button
                    className="primary-button"
                    type="submit"
                    disabled={!onSubmit}
                  >
                    처리 완료로 변경하고 이메일 알림
                  </button>
                </form>
              </>
            ) : (
              <div className="report-detail__empty" role="status">
                <h2 id="report-detail-heading">신고 상세</h2>
                <p>
                  {reports
                    ? '확인할 신고를 선택해 주세요.'
                    : '[신고 상세 데이터가 필요합니다.]'}
                </p>
              </div>
            )}
          </section>
        </div>
      </main>
    </div>
  )
}

export default AdminReportsPage
