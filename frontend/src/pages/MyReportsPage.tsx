// 사용자별 문제 신고 내역 화면
import { useEffect, useState } from 'react'
import {
  listMyReports,
  type ReportPage,
  type ReportSummary,
  type ReportType,
} from '../api/reports'
import AppHeader from '../components/AppHeader'

interface MyReportsPageProps {
  reports?: ReportSummary[]
}

const statusLabels: Record<ReportSummary['status'], string> = {
  OPEN: '확인 전',
  IN_PROGRESS: '확인 중',
  RESOLVED: '처리 완료',
}

const typeLabels: Record<ReportType, string> = {
  WRONG_JUDGMENT: '판정이 잘못된 것 같음',
  IRRELEVANT_EVIDENCE: '근거가 관련 없거나 신뢰하기 어려움',
  BROKEN_EVIDENCE_LINK: '근거 링크가 열리지 않음',
  INACCURATE_HEADLINE: '제목 분석이 부정확함',
  UI_OR_FUNCTION_ERROR: '화면 또는 기능 오류',
}

function MyReportsPage({ reports }: MyReportsPageProps) {
  const [loadedPage, setLoadedPage] = useState<ReportPage>()
  const [currentPage, setCurrentPage] = useState(0)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    if (reports) {
      return
    }
    let active = true
    void listMyReports(currentPage, 20)
      .then((page) => {
        if (active) {
          setLoadedPage(page)
          setLoadError(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setLoadError(
            error instanceof Error
              ? error.message
              : '신고 내역을 불러오지 못했습니다.',
          )
        }
      })
    return () => {
      active = false
    }
  }, [currentPage, reports])

  const visibleReports = reports ?? loadedPage?.items

  return (
    <div className="app-page my-reports-page">
      <AppHeader section="내 신고 내역" />
      <main className="my-reports-content">
        <header>
          <h1>내 신고 내역</h1>
          <p>신고 처리 상태와 관리자 답변을 확인할 수 있습니다.</p>
        </header>
        {loadError ? <p role="alert">{loadError}</p> : null}
        <section className="my-report-list" aria-label="내 신고 목록">
          {visibleReports?.length ? (
            visibleReports.map((report) => (
              <article key={report.id} className="my-report-card">
                <div>
                  <span
                    className={`report-status report-status--${report.status === 'OPEN' ? 'waiting' : report.status === 'IN_PROGRESS' ? 'working' : 'complete'}`}
                  >
                    {statusLabels[report.status]}
                  </span>
                  <small>{typeLabels[report.reportType]}</small>
                  <h2>{report.articleTitle}</h2>
                  <p>
                    {report.publisherName} ·{' '}
                    {report.analysisType === 'HEALTH'
                      ? '건강 뉴스 결과'
                      : '기사 제목 결과'}
                  </p>
                </div>
                <section aria-label="관리자 답변">
                  <h3>관리자 답변</h3>
                  <p>{report.adminReply ?? '아직 등록된 답변이 없습니다.'}</p>
                </section>
              </article>
            ))
          ) : (
            <p role="status">
              {visibleReports
                ? '접수한 신고가 없습니다.'
                : '신고 내역을 불러오고 있습니다.'}
            </p>
          )}
        </section>
        {!reports && loadedPage && loadedPage.totalPages > 1 ? (
          <nav className="saved-pagination" aria-label="신고 내역 페이지">
            <button
              type="button"
              disabled={loadedPage.page === 0}
              onClick={() => setCurrentPage((page) => page - 1)}
            >
              이전 페이지
            </button>
            <span>
              {loadedPage.page + 1} / {loadedPage.totalPages}
            </span>
            <button
              type="button"
              disabled={!loadedPage.hasNext}
              onClick={() => setCurrentPage((page) => page + 1)}
            >
              다음 페이지
            </button>
          </nav>
        ) : null}
      </main>
    </div>
  )
}

export default MyReportsPage
