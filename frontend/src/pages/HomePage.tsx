// 기능 선택 홈 화면
import { Link } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import type { UsageViewData } from '../types/pageData'

interface HomePageProps {
  onStartHealthAnalysis: () => void
  onOpenSavedRecords: () => void
  usage?: UsageViewData
}

function HomePage({
  onStartHealthAnalysis,
  onOpenSavedRecords,
  usage,
}: HomePageProps) {
  return (
    <div className="app-page home-page">
      <AppHeader section="홈">
        <button type="button" onClick={onOpenSavedRecords}>
          저장 기록
        </button>
        <span>내 신고</span>
        <Link to="/login">로그인</Link>
      </AppHeader>

      <main className="home-content">
        <section className="home-hero" aria-labelledby="home-heading">
          <h1
            id="home-heading"
            aria-label="기사의 주장과 제목을 쉽게 확인해 보세요"
          >
            <span>기사의 주장과 제목을</span>
            <span>쉽게 확인해 보세요</span>
          </h1>
          <p>
            국내 언론사의 한국어 기사 링크만 확인할 수 있습니다.
            블로그·카페·SNS는 지원하지 않습니다.
          </p>
          <div className="usage-pill">
            {usage
              ? `오늘 남은 횟수 건강 ${usage.healthRemaining} · 제목 ${usage.headlineRemaining}`
              : '[오늘의 기능별 남은 이용 횟수 데이터가 필요합니다.]'}
          </div>
        </section>

        <section className="feature-grid" aria-label="기사 확인 기능">
          <article className="feature-card feature-card--health">
            <span className="feature-card__badge">공식 기관 · PubMed</span>
            <h2>건강·의학 뉴스 확인</h2>
            <p>
              공신력 있는 근거로 핵심 주장
              <br />
              최대 3개를 확인합니다.
            </p>
            <button
              className="feature-card__button feature-card__button--health"
              type="button"
              onClick={onStartHealthAnalysis}
            >
              복사한 건강 기사 확인하기
            </button>
          </article>

          <article className="feature-card feature-card--title">
            <span className="feature-card__badge">제목 · 본문 비교</span>
            <h2>기사 제목 확인</h2>
            <p>
              제목의 과장, 중요 내용 누락,
              <br />
              본문과의 불일치를 살펴봅니다.
            </p>
            <button
              className="feature-card__button feature-card__button--title"
              type="button"
            >
              복사한 기사 제목 확인하기
            </button>
          </article>
        </section>

        <aside className="home-tip">
          <p>
            처음 사용하시나요? 기사 URL을 복사한 뒤 원하는 기능의 버튼을
            누르세요.
          </p>
          <span>지원 언론사 보기</span>
        </aside>
      </main>
    </div>
  )
}

export default HomePage
