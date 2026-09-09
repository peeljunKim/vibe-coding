// 건강 뉴스 분석 결과 화면
import AppHeader from '../components/AppHeader'
import type { HealthClaimStatus, HealthResultViewData } from '../types/pageData'

interface HealthResultPageProps {
  onNewArticle: () => void
  data?: HealthResultViewData
}

const claimStatus: Record<
  HealthClaimStatus,
  { label: string; className: string }
> = {
  SUPPORTED: { label: '근거 있음', className: 'status-badge--success' },
  NEEDS_REVIEW: { label: '확인 필요', className: 'status-badge--warning' },
  CONTRADICTED: { label: '근거와 충돌', className: 'status-badge--danger' },
  INSUFFICIENT: { label: '자료 부족', className: 'status-badge--neutral' },
}

function HealthResultPage({ onNewArticle, data }: HealthResultPageProps) {
  const status = data ? claimStatus[data.claimStatus] : undefined

  return (
    <div className="app-page health-result-page">
      <AppHeader section="분석 결과">
        <span>도움말</span>
        <span className="header-account-pill">내 정보</span>
      </AppHeader>

      <main className="health-result-content">
        <section
          className="health-result-summary"
          aria-labelledby="health-result-heading"
        >
          <div className="health-result-summary__meta">
            <span
              className={`status-badge ${status?.className ?? 'status-badge--neutral'}`}
            >
              {status?.label ?? '[주장 상태가 필요합니다.]'}
            </span>
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
          <h1 id="health-result-heading">
            {data?.claim ?? '[핵심 주장 데이터가 필요합니다.]'}
          </h1>
          <h2>기사에서 확인된 핵심 주장</h2>
          <section className="verdict-card" aria-labelledby="verdict-heading">
            <h3 id="verdict-heading">판정 요약</h3>
            {data?.reasons.length ? (
              data.reasons.map((reason, index) => (
                <p key={`${index}-${reason}`}>{reason}</p>
              ))
            ) : (
              <p>[주장별 쉬운 판정 이유 데이터가 필요합니다.]</p>
            )}
          </section>

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
            >
              문제가 있다면 결과 신고
            </button>
          </div>
          <div className="result-actions__buttons">
            <button className="secondary-button" type="button" disabled={!data}>
              결과 저장
            </button>
            <button
              className="primary-button"
              type="button"
              onClick={onNewArticle}
            >
              새 기사 확인
            </button>
          </div>
        </section>
      </main>
    </div>
  )
}

export default HealthResultPage
