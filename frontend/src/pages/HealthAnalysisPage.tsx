// 건강 기사 분석 진행 화면
import AppHeader from '../components/AppHeader'
import type { AnalysisStage, HealthAnalysisViewData } from '../types/pageData'

interface HealthAnalysisPageProps {
  onCancel: () => void
  data?: HealthAnalysisViewData
}

const stages: Array<{
  id: AnalysisStage
  title: string
  description: string
}> = [
  {
    id: 'ARTICLE_CHECKED',
    title: '기사 확인',
    description: '지원 언론사·HTTPS·한국어 기사 확인',
  },
  {
    id: 'EVIDENCE_SEARCHING',
    title: '근거 검색',
    description: '공식 기관과 PubMed 자료 검색',
  },
  {
    id: 'RESULT_PREPARING',
    title: '결과 정리',
    description: '핵심 주장과 근거 상태 정리',
  },
]

function HealthAnalysisPage({ onCancel, data }: HealthAnalysisPageProps) {
  const activeStageIndex = data
    ? stages.findIndex((stage) => stage.id === data.stage)
    : -1

  return (
    <div className="app-page analysis-page">
      <AppHeader section="건강·의학 뉴스 확인 / 분석 중" onHome={onCancel}>
        <button type="button" onClick={onCancel}>
          분석 취소
        </button>
      </AppHeader>

      <main className="analysis-content">
        <section className="analysis-hero" aria-labelledby="analysis-heading">
          <span>건강·의학 뉴스 확인</span>
          <h1 id="analysis-heading">기사의 근거를 확인하고 있습니다</h1>
          <p>보통 30~60초, 최대 90초 안에 완료됩니다.</p>
        </section>

        <article className="analysis-article">
          <p>
            {data
              ? [data.article.publisher, data.article.publishedAt]
                  .filter(Boolean)
                  .join(' · ')
              : '[기사 발행사와 발행일 데이터가 필요합니다.]'}
          </p>
          <h2>
            {data?.article.title ??
              '[분석 대상 기사 제목 데이터가 필요합니다.]'}
          </h2>
          <p className="analysis-article__url">
            {data?.article.url ?? '[분석 대상 기사 URL 데이터가 필요합니다.]'}
          </p>
        </article>

        <section className="analysis-progress" aria-label="기사 분석 진행 상태">
          <ol>
            {stages.map((stage, index) => {
              const state =
                index < activeStageIndex
                  ? 'complete'
                  : index === activeStageIndex
                    ? 'active'
                    : 'waiting'

              return (
                <li
                  className={`analysis-step analysis-step--${state}`}
                  key={stage.id}
                >
                  <span className="analysis-step__marker" aria-hidden="true">
                    {state === 'complete'
                      ? '✓'
                      : state === 'active'
                        ? '●'
                        : index + 1}
                  </span>
                  <div>
                    <h2>{`${stage.title} ${state === 'complete' ? '완료' : state === 'active' ? '중' : '대기'}`}</h2>
                    <p>{stage.description}</p>
                  </div>
                </li>
              )
            })}
          </ol>
        </section>

        <aside className="analysis-notice" role="note">
          <strong>안내</strong>
          <p>
            창을 닫거나 새로고침하면 비회원 결과는 사라집니다. 자동 재시도는
            하지 않습니다.
          </p>
        </aside>
      </main>
    </div>
  )
}

export default HealthAnalysisPage
