// 건강 뉴스 분석 결과 화면
import { useState } from 'react'
import { CircleCheck, CircleHelp, CircleX, TriangleAlert } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { CreatedShare } from '../api/shares'
import type { HealthRecordSummary } from '../api/healthRecords'
import type { ReportType } from '../api/reports'
import AppHeader from '../components/AppHeader'
import ReportDialog from '../components/ReportDialog'
import ShareDialog from '../components/ShareDialog'
import type { HealthClaimStatus, HealthResultViewData } from '../types/pageData'

interface HealthResultPageProps {
  onNewArticle: () => void
  onSave?: (analysisId: string) => Promise<HealthRecordSummary>
  onShare?: (recordId: string) => Promise<CreatedShare>
  onRevokeShare?: (token: string) => Promise<void>
  onReport?: (
    analysisId: string,
    reportType: ReportType,
    description: string,
  ) => Promise<void>
  authenticated?: boolean
  data?: HealthResultViewData
  notice?: string | null
}

const claimStatus: Record<
  HealthClaimStatus,
  {
    label: string
    className: string
    tone: string
    icon: typeof CircleCheck
  }
> = {
  SUPPORTED: {
    label: '근거 있음',
    className: 'status-badge--success',
    tone: 'success',
    icon: CircleCheck,
  },
  NEEDS_REVIEW: {
    label: '확인 필요',
    className: 'status-badge--warning',
    tone: 'warning',
    icon: TriangleAlert,
  },
  CONTRADICTED: {
    label: '근거와 충돌',
    className: 'status-badge--danger',
    tone: 'danger',
    icon: CircleX,
  },
  INSUFFICIENT: {
    label: '자료 부족',
    className: 'status-badge--neutral',
    tone: 'neutral',
    icon: CircleHelp,
  },
}

function HealthResultPage({
  onNewArticle,
  onSave,
  onShare,
  onRevokeShare,
  onReport,
  authenticated = false,
  data,
  notice,
}: HealthResultPageProps) {
  const status = data ? claimStatus[data.claimStatus] : undefined
  const [saveStatus, setSaveStatus] = useState<
    'idle' | 'saving' | 'saved' | 'error'
  >('idle')
  const [saveError, setSaveError] = useState<string | null>(null)
  const [reportOpen, setReportOpen] = useState(false)
  const [reportNotice, setReportNotice] = useState<string | null>(null)
  const [shareOpen, setShareOpen] = useState(false)
  const [shareNotice, setShareNotice] = useState<string | null>(null)
  const [savedRecordId, setSavedRecordId] = useState<string | null>(null)
  const [highlightedAnalysisId, setHighlightedAnalysisId] = useState<
    string | null
  >(null)
  const [expandedAnalysisId, setExpandedAnalysisId] = useState<string | null>(
    null,
  )
  const highlightEnabled = Boolean(
    data && highlightedAnalysisId === data.analysisId,
  )
  const claimExpanded = Boolean(data && expandedAnalysisId === data.analysisId)
  const StatusIcon = status?.icon

  const save = async () => {
    if (!data || !onSave || saveStatus === 'saving' || saveStatus === 'saved') {
      return savedRecordId
    }
    setSaveStatus('saving')
    setSaveError(null)
    try {
      const saved = await onSave(data.analysisId)
      setSavedRecordId(saved.id)
      setSaveStatus('saved')
      return saved.id
    } catch (error) {
      setSaveStatus('error')
      setSaveError(
        error instanceof Error
          ? error.message
          : '결과를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
      return null
    }
  }

  const createShare = async () => {
    if (!onShare) {
      throw new Error('공유 링크를 만들 수 없습니다.')
    }
    const recordId = savedRecordId ?? (await save())
    if (!recordId) {
      throw new Error('결과를 저장한 뒤 공유해 주세요.')
    }
    return onShare(recordId)
  }

  return (
    <div className="app-page health-result-page">
      <AppHeader section="분석 결과">
        <Link to="/help">도움말</Link>
        <span className="header-account-pill">내 정보</span>
      </AppHeader>

      <main className="health-result-content">
        <section
          className="health-result-summary"
          aria-labelledby="health-result-heading"
        >
          <div className="health-result-summary__meta">
            {highlightEnabled && status ? (
              <span className={`status-badge ${status.className}`}>
                {status.label}
              </span>
            ) : null}
            <span>
              {data
                ? [
                    data.article.publisher,
                    data.article.publishedAt,
                    data.article.category,
                  ]
                    .filter(Boolean)
                    .join(' · ')
                : '[기사 발행 정보가 필요합니다.]'}
            </span>
          </div>
          <div
            className={`claim-statement${
              data && highlightEnabled && status
                ? ` claim-statement--${status.tone}`
                : ''
            }`}
          >
            <h1 id="health-result-heading">
              {data?.claim ?? '[핵심 주장 데이터가 필요합니다.]'}
            </h1>
            {data && highlightEnabled && status && StatusIcon ? (
              <button
                className="claim-statement-button"
                type="button"
                aria-expanded={claimExpanded}
                aria-controls={
                  claimExpanded ? 'claim-verdict-reason' : undefined
                }
                aria-label={`${data.claim} · ${status.label} · 판정 근거 보기`}
                onClick={() =>
                  setExpandedAnalysisId(claimExpanded ? null : data.analysisId)
                }
              >
                <span className="claim-statement-button__status" aria-hidden>
                  <StatusIcon size={18} strokeWidth={2} />
                  <span>{status.label}</span>
                </span>
              </button>
            ) : null}
          </div>
          <div className="claim-section-heading">
            <h2>기사에서 확인된 핵심 주장</h2>
            <button
              className="verdict-highlight-toggle"
              type="button"
              aria-pressed={highlightEnabled}
              disabled={!data}
              onClick={() => {
                if (!data) return
                setHighlightedAnalysisId(
                  highlightEnabled ? null : data.analysisId,
                )
                setExpandedAnalysisId(null)
              }}
            >
              판정 표시
            </button>
          </div>
          {data && highlightEnabled && claimExpanded ? (
            <section
              id="claim-verdict-reason"
              className="verdict-card"
              aria-labelledby="verdict-heading"
            >
              <h3 id="verdict-heading">판정 이유</h3>
              {data.reasons.length ? (
                data.reasons.map((reason, index) => (
                  <p key={`${index}-${reason}`}>{reason}</p>
                ))
              ) : (
                <p>[주장별 쉬운 판정 이유 데이터가 필요합니다.]</p>
              )}
            </section>
          ) : null}

          <section
            className="evidence-order"
            aria-labelledby="evidence-order-heading"
          >
            <h2 id="evidence-order-heading">근거를 확인한 순서</h2>
            <ol>
              <li>
                <span className="evidence-dot evidence-dot--article" />
                기사 주장
              </li>
              <li>
                <span className="evidence-dot" />
                원문 확인
              </li>
              <li>
                <span className="evidence-dot" />
                연구 비교
              </li>
              <li>
                <span className="evidence-dot evidence-dot--final" />
                종합 판정
              </li>
            </ol>
          </section>
        </section>

        <aside className="source-panel" aria-labelledby="source-panel-heading">
          <div className="source-panel__heading">
            <h2 id="source-panel-heading">확인한 출처</h2>
            <span>{data ? `${data.evidences.length}개 확인` : '확인 전'}</span>
          </div>
          <ul>
            {data?.evidences.length ? (
              data.evidences.map((evidence) => (
                <li key={evidence.id}>
                  <div>
                    <strong>{evidence.provider}</strong>
                    <span>{evidence.title}</span>
                    <small>
                      {[evidence.publishedOrUpdatedDate, evidence.sourceType]
                        .filter(Boolean)
                        .join(' · ')}
                    </small>
                  </div>
                  <a
                    className="source-panel__link"
                    href={evidence.sourceUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    원문 보기 ↗
                  </a>
                </li>
              ))
            ) : (
              <li className="source-panel__empty">
                <p>
                  {data
                    ? '확인된 근거 출처가 없습니다.'
                    : '[근거 출처 데이터가 필요합니다.]'}
                </p>
              </li>
            )}
          </ul>
        </aside>

        <section
          className="result-actions"
          aria-labelledby="result-actions-heading"
        >
          <div>
            <h2 id="result-actions-heading">이 결과가 도움이 되었나요?</h2>
            <p>판정이 아니라 출처와 근거를 기준으로 확인해 주세요.</p>
            <button
              className="text-action text-action--danger"
              type="button"
              disabled={!data}
              onClick={() => {
                if (!authenticated) {
                  setReportNotice('로그인 후 신고할 수 있습니다.')
                  return
                }
                setReportNotice(null)
                setReportOpen(true)
              }}
            >
              문제가 있다면 결과 신고
            </button>
            {reportNotice ? <span role="status">{reportNotice}</span> : null}
          </div>
          <div className="result-actions__buttons">
            {notice ? <span role="alert">{notice}</span> : null}
            {saveError ? <span role="alert">{saveError}</span> : null}
            {saveStatus === 'saved' ? (
              <span role="status">결과가 저장되었습니다.</span>
            ) : null}
            <button
              className="secondary-button"
              type="button"
              disabled={
                !data ||
                !onSave ||
                saveStatus === 'saving' ||
                saveStatus === 'saved'
              }
              onClick={() => void save()}
            >
              {saveStatus === 'saving'
                ? '저장 중'
                : saveStatus === 'saved'
                  ? '저장 완료'
                  : '결과 저장'}
            </button>
            <button
              className="secondary-button"
              type="button"
              disabled={!data || !onShare || !onRevokeShare}
              onClick={() => {
                if (!authenticated) {
                  setShareNotice('로그인 후 결과를 공유할 수 있습니다.')
                  return
                }
                setShareNotice(null)
                setShareOpen(true)
              }}
            >
              결과 공유
            </button>
            <button
              className="primary-button"
              type="button"
              onClick={onNewArticle}
            >
              새 기사 확인
            </button>
            {shareNotice ? <span role="status">{shareNotice}</span> : null}
          </div>
        </section>
      </main>
      {reportOpen && data && onReport ? (
        <ReportDialog
          onClose={() => setReportOpen(false)}
          onSubmit={(reportType, description) =>
            onReport(data.analysisId, reportType, description)
          }
        />
      ) : null}
      {shareOpen && data && onShare && onRevokeShare ? (
        <ShareDialog
          requiresSave={!savedRecordId}
          onClose={() => setShareOpen(false)}
          onCreate={createShare}
          onRevoke={onRevokeShare}
        />
      ) : null}
    </div>
  )
}

export default HealthResultPage
