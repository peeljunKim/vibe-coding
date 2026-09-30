// 기사체크 이용 도움말 화면
import { Link } from 'react-router-dom'
import AppHeader from '../components/AppHeader'

function HelpPage() {
  return (
    <div className="app-page help-page">
      <AppHeader section="도움말">
        <Link to="/">홈으로</Link>
      </AppHeader>

      <main className="help-content">
        <section className="help-hero" aria-labelledby="help-heading">
          <div>
            <span className="help-eyebrow">처음 사용하는 분을 위한 안내</span>
            <h1 id="help-heading">기사체크를 이렇게 이용하세요</h1>
            <p>
              국내 언론사의 공개된 한국어 기사 URL을 복사한 뒤, 확인하려는
              내용에 맞는 기능을 선택하세요.
            </p>
          </div>
          <Link className="primary-button help-hero__action" to="/">
            기사 확인 시작하기
          </Link>
        </section>

        <ol className="help-flow" aria-label="기사 확인 순서">
          <li>
            <span aria-hidden="true">1</span>
            <div>
              <strong>URL 복사</strong>
              <p>지원 언론사의 기사 주소를 브라우저에서 복사합니다.</p>
            </div>
          </li>
          <li>
            <span aria-hidden="true">2</span>
            <div>
              <strong>기능 선택</strong>
              <p>건강 주장 확인과 기사 제목 확인 중 하나를 선택합니다.</p>
            </div>
          </li>
          <li>
            <span aria-hidden="true">3</span>
            <div>
              <strong>결과 읽기</strong>
              <p>판정 이유와 근거를 확인하고 원문도 함께 살펴봅니다.</p>
            </div>
          </li>
        </ol>

        <section className="help-feature-grid" aria-label="기능별 이용 안내">
          <article className="help-feature-card help-feature-card--health">
            <h2>건강·의학 뉴스 확인</h2>
            <p>
              건강·의학·보건 관련 기사의 핵심 주장을 최대 3개까지 추려 공신력
              있는 자료와 비교합니다.
            </p>
            <ul>
              <li>지원 언론사의 건강·의학 기사만 확인</li>
              <li>주장별 판정 이유와 근거 출처 제공</li>
              <li>로그인 회원 하루 5회·비회원 하루 2회</li>
            </ul>
          </article>

          <article className="help-feature-card help-feature-card--title">
            <h2>기사 제목 확인</h2>
            <p>
              지원 언론사의 모든 분야 기사에서 제목의 과장, 중요 내용 누락,
              본문과의 불일치를 살펴봅니다.
            </p>
            <ul>
              <li>외부 의료 자료를 사용하지 않는 제목 비교</li>
              <li>문제가 있을 때만 중립적인 대체 제목 제안</li>
              <li>로그인 회원 하루 10회·비회원 하루 5회</li>
            </ul>
          </article>
        </section>

        <section
          className="help-detail-grid"
          aria-label="결과와 이용 제한 안내"
        >
          <article>
            <h2>결과는 이렇게 읽어 주세요</h2>
            <dl>
              <div>
                <dt>주장 상태</dt>
                <dd>
                  근거 있음·확인 필요·근거와 충돌·자료 부족으로 구분합니다.
                </dd>
              </div>
              <div>
                <dt>핵심 주장 확인률</dt>
                <dd>
                  근거로 확인된 주장 비율이며, 사실일 확률이나 AI 신뢰 확률이
                  아닙니다.
                </dd>
              </div>
              <div>
                <dt>원문과 근거</dt>
                <dd>
                  판정만 보지 말고 기사 원문과 연결된 근거도 함께 확인합니다.
                </dd>
              </div>
            </dl>
          </article>

          <article className="help-notice-card">
            <h2>사용 전에 확인해 주세요</h2>
            <ul>
              <li>
                블로그·카페·SNS와 로그인·결제가 필요한 기사는 지원하지 않습니다.
              </li>
              <li>
                지원 상태는 홈의 ‘지원 언론사 보기’에서 확인할 수 있습니다.
              </li>
              <li>
                두 기능의 이용 횟수는 따로 계산하고 한국시간 자정에
                초기화합니다.
              </li>
              <li>
                의료 정보는 참고용이며 의사 또는 약사의 진단을 대신하지
                않습니다.
              </li>
            </ul>
          </article>
        </section>
      </main>
    </div>
  )
}

export default HelpPage
