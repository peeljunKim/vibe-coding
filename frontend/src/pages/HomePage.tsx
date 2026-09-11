// 기능 선택 홈 화면
import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import type { UsageViewData } from '../types/pageData'

const activePublishers = [
  '연합뉴스',
  'MBC',
  'SBS',
  '중앙일보',
  '한겨레',
  '경향신문',
  '국민일보',
  '매일경제',
  '한국경제',
]

const unsupportedPublishers = [
  '뉴시스',
  'KBS',
  'YTN',
  'JTBC',
  '조선일보',
  '동아일보',
  '한국일보',
  '서울신문',
  '헬스조선',
  '코메디닷컴',
  '메디칼타임즈',
]

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
  const [isPublisherDirectoryOpen, setPublisherDirectoryOpen] = useState(false)
  const publisherDirectoryButtonRef = useRef<HTMLButtonElement>(null)

  const closePublisherDirectory = () => {
    setPublisherDirectoryOpen(false)
    publisherDirectoryButtonRef.current?.focus()
  }

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

        <section
          className={`publisher-directory${
            isPublisherDirectoryOpen ? ' publisher-directory--open' : ''
          }`}
          aria-labelledby={
            isPublisherDirectoryOpen ? 'publisher-directory-heading' : undefined
          }
          onKeyDown={(event) => {
            if (isPublisherDirectoryOpen && event.key === 'Escape') {
              closePublisherDirectory()
            }
          }}
        >
          <div className="publisher-directory__summary">
            {isPublisherDirectoryOpen ? (
              <div>
                <h2 id="publisher-directory-heading">지원 언론사</h2>
                <p>
                  건강·의학 뉴스 확인 기능에서 현재 사용할 수 있는 언론사와 보강
                  상태입니다.
                </p>
              </div>
            ) : (
              <p>
                처음 사용하시나요? 기사 URL을 복사한 뒤 원하는 기능의 버튼을
                누르세요.
              </p>
            )}

            <button
              ref={publisherDirectoryButtonRef}
              className="publisher-directory__toggle"
              type="button"
              aria-expanded={isPublisherDirectoryOpen}
              aria-controls="publisher-directory-content"
              onClick={() =>
                setPublisherDirectoryOpen((currentValue) => !currentValue)
              }
            >
              {isPublisherDirectoryOpen ? '접기 ↑' : '지원 언론사 보기'}
            </button>
          </div>

          {isPublisherDirectoryOpen && (
            <div
              id="publisher-directory-content"
              className="publisher-directory__content"
            >
              <article className="publisher-status-card publisher-status-card--active">
                <h3>지원 중 · {activePublishers.length}</h3>
                <div className="publisher-group">
                  <strong>통신·방송</strong>
                  <ul>
                    {activePublishers.slice(0, 3).map((publisher) => (
                      <li key={publisher}>{publisher}</li>
                    ))}
                  </ul>
                </div>
                <div className="publisher-group">
                  <strong>신문·경제</strong>
                  <ul>
                    {activePublishers.slice(3).map((publisher) => (
                      <li key={publisher}>{publisher}</li>
                    ))}
                  </ul>
                </div>
              </article>

              <article className="publisher-status-card publisher-status-card--paused">
                <h3>일시 중단 · 0</h3>
                <strong>현재 일시 중단된 언론사가 없습니다.</strong>
                <p>
                  추출 장애가 확인되면 상태와 함께 이 영역에 즉시 안내합니다.
                </p>
              </article>

              <article className="publisher-status-card publisher-status-card--unsupported">
                <h3>현재 미지원 · {unsupportedPublishers.length}</h3>
                <ul>
                  {unsupportedPublishers.map((publisher) => (
                    <li key={publisher}>{publisher}</li>
                  ))}
                </ul>
                <p>추출 보완과 재시험 후 지원 여부를 갱신합니다.</p>
              </article>

              <p className="publisher-directory__notice">
                새 언론사는 지속적으로 보강하며, 지원 상태가 바뀌면 이 목록에
                반영합니다.
              </p>
            </div>
          )}
        </section>
      </main>
    </div>
  )
}

export default HomePage
