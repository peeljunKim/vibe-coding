// 기능 선택 홈 화면
import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import type { PublisherViewData, UsageViewData } from '../types/pageData'

type PublisherLoadStatus = 'idle' | 'loading' | 'success' | 'error'

const publisherCategoryGroups = [
  {
    label: '통신·방송',
    categories: ['NEWS_AGENCY', 'BROADCAST_NEWS'],
  },
  {
    label: '신문·경제',
    categories: ['GENERAL_NEWSPAPER', 'BUSINESS_NEWSPAPER'],
  },
  {
    label: '건강·의료',
    categories: ['HEALTH_MEDICAL'],
  },
] as const

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
  const [publishers, setPublishers] = useState<PublisherViewData[]>([])
  const [publisherLoadStatus, setPublisherLoadStatus] =
    useState<PublisherLoadStatus>('idle')
  const publisherDirectoryButtonRef = useRef<HTMLButtonElement>(null)

  const activePublishers = publishers.filter(
    (publisher) => publisher.status === 'ACTIVE',
  )
  const pausedPublishers = publishers.filter(
    (publisher) => publisher.status === 'TEMPORARILY_DISABLED',
  )
  const unsupportedPublishers = publishers.filter(
    (publisher) => publisher.status === 'UNSUPPORTED',
  )

  const loadPublisherDirectory = async () => {
    setPublisherLoadStatus('loading')

    try {
      const response = await fetch('/api/publishers')
      if (!response.ok) {
        throw new Error('Publisher directory request failed')
      }

      const responseBody: unknown = await response.json()
      if (!Array.isArray(responseBody)) {
        throw new Error('Publisher directory response is not an array')
      }

      setPublishers(responseBody as PublisherViewData[])
      setPublisherLoadStatus('success')
    } catch {
      setPublishers([])
      setPublisherLoadStatus('error')
    }
  }

  const closePublisherDirectory = () => {
    setPublisherDirectoryOpen(false)
    publisherDirectoryButtonRef.current?.focus()
  }

  const togglePublisherDirectory = () => {
    if (isPublisherDirectoryOpen) {
      closePublisherDirectory()
      return
    }

    setPublisherDirectoryOpen(true)
    if (publisherLoadStatus === 'idle' || publisherLoadStatus === 'error') {
      void loadPublisherDirectory()
    }
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
              onClick={togglePublisherDirectory}
            >
              {isPublisherDirectoryOpen ? '접기 ↑' : '지원 언론사 보기'}
            </button>
          </div>

          {isPublisherDirectoryOpen && publisherLoadStatus === 'loading' && (
            <div
              id="publisher-directory-content"
              className="publisher-directory__content"
            >
              <p
                className="publisher-directory__state"
                role="status"
                aria-label="지원 언론사 정보를 불러오는 중입니다."
              >
                지원 언론사 정보를 불러오는 중입니다.
              </p>
            </div>
          )}

          {isPublisherDirectoryOpen && publisherLoadStatus === 'error' && (
            <div
              id="publisher-directory-content"
              className="publisher-directory__content"
            >
              <p
                className="publisher-directory__state publisher-directory__state--error"
                role="alert"
                aria-label="지원 언론사 정보를 불러오지 못했습니다."
              >
                지원 언론사 정보를 불러오지 못했습니다. 잠시 후 다시 시도해
                주세요.
              </p>
            </div>
          )}

          {isPublisherDirectoryOpen &&
            publisherLoadStatus === 'success' &&
            publishers.length === 0 && (
              <div
                id="publisher-directory-content"
                className="publisher-directory__content"
              >
                <p className="publisher-directory__state">
                  현재 등록된 언론사가 없습니다.
                </p>
              </div>
            )}

          {isPublisherDirectoryOpen &&
            publisherLoadStatus === 'success' &&
            publishers.length > 0 && (
              <div
                id="publisher-directory-content"
                className="publisher-directory__content"
              >
                <article className="publisher-status-card publisher-status-card--active">
                  <h3>지원 중 · {activePublishers.length}</h3>
                  {activePublishers.length === 0 ? (
                    <strong>현재 지원 중인 언론사가 없습니다.</strong>
                  ) : (
                    publisherCategoryGroups.map((group) => {
                      const groupPublishers = activePublishers.filter(
                        (publisher) =>
                          group.categories.some(
                            (category) => category === publisher.category,
                          ),
                      )

                      return groupPublishers.length > 0 ? (
                        <div className="publisher-group" key={group.label}>
                          <strong>{group.label}</strong>
                          <ul>
                            {groupPublishers.map((publisher) => (
                              <li key={publisher.name}>{publisher.name}</li>
                            ))}
                          </ul>
                        </div>
                      ) : null
                    })
                  )}
                </article>

                <article className="publisher-status-card publisher-status-card--paused">
                  <h3>일시 중단 · {pausedPublishers.length}</h3>
                  {pausedPublishers.length === 0 ? (
                    <strong>현재 일시 중단된 언론사가 없습니다.</strong>
                  ) : (
                    <ul>
                      {pausedPublishers.map((publisher) => (
                        <li key={publisher.name}>{publisher.name}</li>
                      ))}
                    </ul>
                  )}
                  <p>
                    추출 장애가 확인되면 상태와 함께 이 영역에 즉시 안내합니다.
                  </p>
                </article>

                <article className="publisher-status-card publisher-status-card--unsupported">
                  <h3>현재 미지원 · {unsupportedPublishers.length}</h3>
                  <ul>
                    {unsupportedPublishers.map((publisher) => (
                      <li key={publisher.name}>{publisher.name}</li>
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
