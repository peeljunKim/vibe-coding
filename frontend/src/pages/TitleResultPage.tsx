// 기사 제목 분석 결과 화면
import AppHeader from '../components/AppHeader'
import type { HeadlineIssueType, TitleResultViewData } from '../types/pageData'

interface TitleResultPageProps {
  data?: TitleResultViewData
}

const issueLabels: Record<HeadlineIssueType, string> = {
  NO_ISSUE: '문제 없음',
  EXAGGERATED: '과장 표현',
  OMITS_CONTEXT: '중요 내용 누락',
  MISMATCH: '본문과 불일치',
}

function TitleResultPage({ data }: TitleResultPageProps) {
  const issueCount =
    data?.issues.filter((issue) => issue.type !== 'NO_ISSUE').length ?? 0
  const hasIssue = issueCount > 0
  const resultSummary = data
    ? hasIssue
      ? `${data.issues.map((issue) => issueLabels[issue.type]).join('·')}을 확인했습니다.`
      : '문제를 발견하지 않았습니다.'
    : '[기사 제목 분석 결과 데이터가 필요합니다.]'

  return (
    <div className="app-page title-result-page">
      <AppHeader section="기사 제목 결과">
        <span>공유</span>
        <span>문제 신고</span>
      </AppHeader>

      <main className="title-result-content">
        <section
          className="original-title-card"
          aria-labelledby="original-title-heading"
        >
          <span>기존 제목</span>
          <h1 id="original-title-heading">
            {data?.article.title ?? '[분석한 기사 제목 데이터가 필요합니다.]'}
          </h1>
        </section>

        <section
          className="title-verdict"
          aria-labelledby="title-verdict-heading"
        >
          <span>분석 결과</span>
          <h2 id="title-verdict-heading">{resultSummary}</h2>
          <p>AI가 분석한 참고용 결과입니다.</p>
        </section>

        <section
          className="title-issues"
          aria-labelledby="title-issues-heading"
        >
          <h2 id="title-issues-heading">
            {data ? `발견한 문제 ${issueCount}개` : '발견한 문제'}
          </h2>
          <div className="title-issues__grid">
            {data?.issues.length ? (
              data.issues.map((issue, index) => (
                <article
                  className={`title-issue title-issue--${issue.type === 'EXAGGERATED' ? 'warning' : issue.type === 'NO_ISSUE' ? 'success' : 'danger'}`}
                  key={`${issue.type}-${index}`}
                >
                  <h3>{issueLabels[issue.type]}</h3>
                  <p>{issue.explanation}</p>
                </article>
              ))
            ) : (
              <article className="title-issue title-issue--empty">
                <p>
                  {data
                    ? '발견된 제목 문제가 없습니다.'
                    : '[제목 문제 유형과 설명 데이터가 필요합니다.]'}
                </p>
              </article>
            )}
          </div>
        </section>

        <section
          className="neutral-title-card"
          aria-labelledby="neutral-title-heading"
        >
          <div>
            <span>중립적인 대체 제목</span>
            <h2 id="neutral-title-heading">
              {data?.alternativeHeadline ??
                (data
                  ? '문제 없음으로 대체 제목을 생성하지 않습니다.'
                  : '[대체 제목 데이터가 필요합니다.]')}
            </h2>
          </div>
          <button
            className="primary-button"
            type="button"
            disabled={!data?.alternativeHeadline}
            onClick={() => {
              if (data?.alternativeHeadline) {
                void navigator.clipboard.writeText(data.alternativeHeadline)
              }
            }}
          >
            대체 제목 복사
          </button>
        </section>

        <p className="title-result-note">
          기사 제목 확인 결과는 히스토리에 저장되지 않으며, 공유 데이터만 7일
          동안 보관됩니다.
        </p>
      </main>
    </div>
  )
}

export default TitleResultPage
