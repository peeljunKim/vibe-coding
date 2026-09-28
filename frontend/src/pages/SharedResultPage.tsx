// 비로그인 공유 결과 화면
import { useEffect, useState } from 'react'
import {
  getSharedHeadlineResult,
  getSharedHealthResult,
  type SharedHeadlineResult,
  type SharedHealthResult,
} from '../api/shares'

interface SharedResultPageProps {
  type: 'health' | 'headline'
}

type SharedResult = SharedHealthResult | SharedHeadlineResult

const healthStatusLabel = {
  RELIABLE: '근거 충분',
  CAUTION: '주의 필요',
  DOUBTFUL: '근거 부족',
}

const claimStatusLabel = {
  SUPPORTED: '근거 확인',
  NEEDS_REVIEW: '추가 확인 필요',
  CONTRADICTED: '상반된 근거',
  INSUFFICIENT: '근거 부족',
}

const issueLabel = {
  NO_ISSUE: '문제 없음',
  EXAGGERATED: '과장 표현',
  OMITS_CONTEXT: '중요 내용 누락',
  MISMATCH: '본문과 불일치',
}

function SharedResultPage({ type }: SharedResultPageProps) {
  const [result, setResult] = useState<SharedResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const robots = document.createElement('meta')
    robots.name = 'robots'
    robots.content = 'noindex,nofollow,noarchive'
    document.head.appendChild(robots)

    let active = true
    const token = window.location.hash.slice(1)
    const request =
      type === 'health'
        ? getSharedHealthResult(token)
        : getSharedHeadlineResult(token)
    void request
      .then((data) => {
        if (active) setResult(data)
      })
      .catch(() => {
        if (active) setError('공유 결과를 확인할 수 없습니다.')
      })
    return () => {
      active = false
      robots.remove()
    }
  }, [type])

  return (
    <div className="app-shell shared-result-page">
      <header className="site-header">
        <div className="site-header__inner">
          <a className="site-header__brand" href="/">
            기사체크
          </a>
          <span className="site-header__divider" aria-hidden="true" />
          <span className="site-header__section">공유 결과</span>
        </div>
      </header>

      <main className="shared-result-content">
        {error ? (
          <section className="shared-result-error" role="alert">
            <h1>공유 결과를 확인할 수 없습니다.</h1>
            <p>링크가 만료되었거나 공유가 해제되었을 수 있습니다.</p>
          </section>
        ) : result ? (
          <>
            <header className="shared-result-heading">
              <span>읽기 전용 공유 결과</span>
              <h1>{result.article.title}</h1>
              <p>
                {result.article.publisher} · 분석일{' '}
                {new Date(result.analyzedAt).toLocaleDateString('ko-KR')}
              </p>
            </header>
            {result.shareType === 'HEALTH' ? (
              <HealthSharedResult result={result} />
            ) : (
              <HeadlineSharedResult result={result} />
            )}
            <p className="shared-result-login-notice">
              로그인 후 신고할 수 있습니다.
            </p>
          </>
        ) : (
          <p className="shared-result-loading" role="status">
            공유 결과를 불러오는 중입니다.
          </p>
        )}
      </main>
    </div>
  )
}

function HealthSharedResult({ result }: { result: SharedHealthResult }) {
  return (
    <section className="shared-health-result" aria-label="건강 분석 공유 결과">
      <div className="shared-result-verdict">
        <span>{healthStatusLabel[result.overallStatus]}</span>
        <strong>
          확인된 주장 {result.confirmedClaimCount}/{result.totalClaimCount}
        </strong>
      </div>
      <div className="shared-claim-list">
        {result.claims.map((claim) => (
          <article key={claim.order} className="shared-claim-card">
            <span>{claimStatusLabel[claim.status]}</span>
            <h2>{claim.claim}</h2>
            <p>{claim.reason}</p>
            {claim.evidences.length > 0 ? (
              <div className="shared-evidence-list">
                {claim.evidences.map((evidence) => (
                  <section key={evidence.sourceUrl}>
                    <h3>{evidence.title}</h3>
                    <p>
                      {evidence.provider}
                      {evidence.publishedOrUpdatedDate
                        ? ` · ${evidence.publishedOrUpdatedDate}`
                        : ''}
                    </p>
                    <p>{evidence.summary}</p>
                    <a
                      href={evidence.sourceUrl}
                      target="_blank"
                      rel="noreferrer"
                    >
                      근거 원문 보기 ↗
                    </a>
                  </section>
                ))}
              </div>
            ) : null}
          </article>
        ))}
      </div>
    </section>
  )
}

function HeadlineSharedResult({ result }: { result: SharedHeadlineResult }) {
  return (
    <section className="shared-headline-result" aria-label="기사 제목 공유 결과">
      <h2>제목 분석 결과</h2>
      <div className="shared-headline-issues">
        {result.issues.map((issue) => (
          <article key={issue.type}>
            <strong>{issueLabel[issue.type]}</strong>
            <p>{issue.explanation}</p>
          </article>
        ))}
      </div>
      {result.alternativeHeadline ? (
        <section className="shared-alternative-title">
          <span>중립적인 대체 제목</span>
          <h3>{result.alternativeHeadline}</h3>
        </section>
      ) : null}
      <a href={result.article.url} target="_blank" rel="noreferrer">
        기사 원문 보기 ↗
      </a>
    </section>
  )
}

export default SharedResultPage
