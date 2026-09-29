// 관리자 신고 처리 화면
import { useEffect, useState } from 'react'
import {
  getAdminReport,
  listAdminReports,
  updateAdminReport,
  type ReportDetail,
  type ReportPage,
  type ReportSummary,
  type ReportType,
} from '../api/reports'
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

const statusLabels = {
  OPEN: '확인 전',
  IN_PROGRESS: '확인 중',
  RESOLVED: '처리 완료',
} as const

const apiStatuses = {
  '확인 전': 'OPEN',
  '확인 중': 'IN_PROGRESS',
  '처리 완료': 'RESOLVED',
} as const

const typeLabels: Record<ReportType, string> = {
  WRONG_JUDGMENT: '판정이 잘못된 것 같음',
  IRRELEVANT_EVIDENCE: '근거가 관련 없거나 신뢰하기 어려움',
  BROKEN_EVIDENCE_LINK: '근거 링크가 열리지 않음',
  INACCURATE_HEADLINE: '제목 분석이 부정확함',
  UI_OR_FUNCTION_ERROR: '화면 또는 기능 오류',
}

const toViewReport = (
  report: ReportSummary,
  detail?: ReportDetail,
): ReportSummaryViewData => {
  const answer = detail?.adminReply ?? report.adminReply
  return {
    id: report.id,
    title: typeLabels[report.reportType],
    status: statusLabels[detail?.status ?? report.status],
    reportedAt: new Date(report.createdAt).toLocaleDateString('ko-KR'),
    publisher: report.publisherName,
    analysisType:
      report.analysisType === 'HEALTH' ? '건강 뉴스 결과' : '기사 제목 결과',
    description: detail?.description ?? '',
    ...(answer ? { answer } : {}),
  }
}

const recordValue = (value: unknown): Record<string, unknown> | undefined =>
  typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined

const stringValue = (value: unknown) =>
  typeof value === 'string' && value.length > 0 ? value : undefined

const listValue = (value: unknown): unknown[] =>
  Array.isArray(value) ? (value as unknown[]) : []

function ReportSnapshot({ detail }: { detail: ReportDetail }) {
  const result = recordValue(detail.resultSnapshot.result)
  const claims = listValue(result?.claims)
    .map(recordValue)
    .filter((value): value is Record<string, unknown> => value !== undefined)
  const issues = listValue(result?.issues)
    .map(recordValue)
    .filter((value): value is Record<string, unknown> => value !== undefined)
  const alternativeHeadline = stringValue(result?.alternativeHeadline)

  return (
    <section
      className="report-snapshot"
      aria-labelledby="report-snapshot-heading"
    >
      <div className="report-snapshot__heading">
        <h3 id="report-snapshot-heading">신고 당시 분석 결과</h3>
        <a href={detail.articleUrl} target="_blank" rel="noreferrer">
          신고 당시 기사 열기
        </a>
      </div>
      {detail.analysisType === 'HEALTH' ? (
        claims.length ? (
          <div className="report-snapshot__items">
            {claims.map((claim, claimIndex) => {
              const evidences = listValue(claim?.evidences)
                .map(recordValue)
                .filter(
                  (value): value is Record<string, unknown> =>
                    value !== undefined,
                )
              return (
                <article
                  key={`${stringValue(claim?.claim) ?? '주장'}-${claimIndex}`}
                >
                  <strong>{stringValue(claim?.claim) ?? '확인할 주장'}</strong>
                  {stringValue(claim?.reason) ? (
                    <p>{stringValue(claim?.reason)}</p>
                  ) : null}
                  {evidences.length ? (
                    <ul>
                      {evidences.map((evidence, evidenceIndex) => {
                        const title =
                          stringValue(evidence?.title) ?? '확인 근거'
                        const sourceUrl = stringValue(evidence?.sourceUrl)
                        return (
                          <li key={`${title}-${evidenceIndex}`}>
                            {sourceUrl ? (
                              <a
                                href={sourceUrl}
                                target="_blank"
                                rel="noreferrer"
                              >
                                {title} 원문 보기
                              </a>
                            ) : (
                              <span>{title}</span>
                            )}
                            {stringValue(evidence?.summary) ? (
                              <p>{stringValue(evidence?.summary)}</p>
                            ) : null}
                          </li>
                        )
                      })}
                    </ul>
                  ) : null}
                </article>
              )
            })}
          </div>
        ) : (
          <p>저장된 주장과 근거가 없습니다.</p>
        )
      ) : issues.length || alternativeHeadline ? (
        <div className="report-snapshot__items">
          {issues.map((issue, issueIndex) => (
            <article
              key={`${stringValue(issue?.type) ?? '제목 문제'}-${issueIndex}`}
            >
              <strong>{stringValue(issue?.type) ?? '제목 문제'}</strong>
              {stringValue(issue?.explanation) ? (
                <p>{stringValue(issue?.explanation)}</p>
              ) : null}
            </article>
          ))}
          {alternativeHeadline ? (
            <article>
              <strong>대체 제목</strong>
              <p>{alternativeHeadline}</p>
            </article>
          ) : null}
        </div>
      ) : (
        <p>저장된 제목 분석 결과가 없습니다.</p>
      )}
    </section>
  )
}

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
  const [loadedPage, setLoadedPage] = useState<ReportPage>()
  const [currentPage, setCurrentPage] = useState(0)
  const [details, setDetails] = useState<Record<string, ReportDetail>>({})
  const [loadError, setLoadError] = useState<string | null>(null)
  const [detailLoadError, setDetailLoadError] = useState<string | null>(null)
  const [detailReloadVersion, setDetailReloadVersion] = useState(0)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const visibleReports =
    reports ??
    loadedPage?.items.map((report) => toViewReport(report, details[report.id]))
  const visibleStats =
    stats ??
    (visibleReports
      ? {
          waiting: visibleReports.filter(
            (report) => report.status === '확인 전',
          ).length,
          working: visibleReports.filter(
            (report) => report.status === '확인 중',
          ).length,
          complete: visibleReports.filter(
            (report) => report.status === '처리 완료',
          ).length,
        }
      : undefined)
  const selectedReport =
    visibleReports?.find((report) => report.id === selectedId) ??
    visibleReports?.[0]
  const selectedReportId = selectedReport?.id
  const selectedDetail = selectedReportId
    ? details[selectedReportId]
    : undefined

  useEffect(() => {
    if (reports) {
      return
    }
    let active = true
    void listAdminReports(currentPage, 20)
      .then((page) => {
        if (!active) {
          return
        }
        setLoadedPage(page)
        setSelectedId(undefined)
        setLoadError(null)
        setDetailLoadError(null)
      })
      .catch((error: unknown) => {
        if (active) {
          setLoadError(
            error instanceof Error
              ? error.message
              : '신고 목록을 불러오지 못했습니다.',
          )
        }
      })
    return () => {
      active = false
    }
  }, [currentPage, reports])

  useEffect(() => {
    if (reports || !selectedReportId || details[selectedReportId]) {
      return
    }
    let active = true
    void getAdminReport(selectedReportId)
      .then((detail) => {
        if (!active) {
          return
        }
        setDetails((current) => ({ ...current, [detail.id]: detail }))
        setDetailLoadError(null)
      })
      .catch((error: unknown) => {
        if (active) {
          setDetailLoadError(
            error instanceof Error
              ? error.message
              : '신고 상세를 불러오지 못했습니다.',
          )
        }
      })
    return () => {
      active = false
    }
  }, [detailReloadVersion, details, reports, selectedReportId])

  const submit = (
    report: ReportSummaryViewData,
    status: ReportStatus,
    answer: string,
  ) => {
    if (onSubmit) {
      onSubmit(report.id, status, answer)
      return
    }
    const detail = details[report.id]
    if (!detail || status === '확인 전') {
      return
    }
    setSubmitting(true)
    setSubmitError(null)
    void updateAdminReport(report.id, {
      status: apiStatuses[status],
      adminReply: answer.trim() || null,
      version: detail.version,
    })
      .then((updated) => {
        setDetails((current) => ({ ...current, [updated.id]: updated }))
        setLoadedPage((current) =>
          current
            ? {
                ...current,
                items: current.items.map((item) =>
                  item.id === updated.id ? updated : item,
                ),
              }
            : current,
        )
      })
      .catch((error: unknown) => {
        setDetails((current) => {
          const refreshed = { ...current }
          delete refreshed[report.id]
          return refreshed
        })
        setSubmitError(
          error instanceof Error
            ? error.message
            : '신고 상태를 변경하지 못했습니다.',
        )
      })
      .finally(() => setSubmitting(false))
  }

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
            <strong>{visibleStats ? `${visibleStats.waiting}건` : '-'}</strong>
          </article>
          <article className="report-stat report-stat--working">
            <span>확인 중</span>
            <strong>{visibleStats ? `${visibleStats.working}건` : '-'}</strong>
          </article>
          <article className="report-stat report-stat--complete">
            <span>처리 완료</span>
            <strong>{visibleStats ? `${visibleStats.complete}건` : '-'}</strong>
          </article>
        </section>
        {!stats ? <p className="report-stats-note">현재 페이지 기준</p> : null}

        <div className="report-workspace">
          <section
            className="report-list"
            aria-labelledby="report-list-heading"
          >
            <h2 id="report-list-heading">신고 목록</h2>
            <div>
              {visibleReports?.length ? (
                visibleReports.map((report) => (
                  <button
                    type="button"
                    key={report.id}
                    onClick={() => {
                      setDetailLoadError(null)
                      setSelectedId(report.id)
                    }}
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
                  {visibleReports
                    ? '접수된 신고가 없습니다.'
                    : (loadError ?? '신고 목록을 불러오고 있습니다.')}
                </p>
              )}
            </div>
            {!reports && loadedPage && loadedPage.totalPages > 1 ? (
              <nav
                className="saved-pagination"
                aria-label="관리자 신고 목록 페이지"
              >
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

                {selectedDetail ? (
                  <ReportSnapshot detail={selectedDetail} />
                ) : null}

                {detailLoadError ? (
                  <div className="report-detail__retry">
                    <p role="alert">{detailLoadError}</p>
                    <button
                      type="button"
                      onClick={() => {
                        setDetailLoadError(null)
                        setDetailReloadVersion((version) => version + 1)
                      }}
                    >
                      신고 상세 다시 불러오기
                    </button>
                  </div>
                ) : null}

                <form
                  onSubmit={(event) => {
                    event.preventDefault()
                    const formData = new FormData(event.currentTarget)
                    const status = formData.get('status')
                    const answer = formData.get('answer')
                    if (!isReportStatus(status)) {
                      return
                    }
                    submit(
                      selectedReport,
                      status,
                      typeof answer === 'string' ? answer : '',
                    )
                  }}
                >
                  <label htmlFor="report-status">처리 상태</label>
                  <select
                    id="report-status"
                    name="status"
                    key={`${selectedReport.id}-${selectedDetail?.version ?? 'loading'}`}
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
                    key={`answer-${selectedReport.id}-${selectedReport.answer ?? ''}`}
                    defaultValue={selectedReport.answer ?? ''}
                    placeholder="관리자 답변을 입력하세요"
                  />
                  {submitError ? <p role="alert">{submitError}</p> : null}
                  <button
                    className="primary-button"
                    type="submit"
                    disabled={
                      submitting ||
                      (!onSubmit && !details[selectedReport.id]) ||
                      selectedReport.status === '처리 완료'
                    }
                  >
                    {submitting ? '변경 중' : '상태와 답변 저장'}
                  </button>
                </form>
              </>
            ) : (
              <div className="report-detail__empty" role="status">
                <h2 id="report-detail-heading">신고 상세</h2>
                <p>
                  {visibleReports
                    ? '확인할 신고를 선택해 주세요.'
                    : (loadError ?? '신고 상세를 불러오고 있습니다.')}
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
