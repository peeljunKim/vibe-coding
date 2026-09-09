// 사용자 로그인 화면
import AppHeader from '../components/AppHeader'
import googleLoginImage from '../assets/oauth/google-login.png'
import kakaoLoginImage from '../assets/oauth/kakao-login.png'
import naverLoginImage from '../assets/oauth/naver-login.png'

const backendBaseUrl = (
  import.meta.env.VITE_BACKEND_BASE_URL ?? 'http://localhost:8080'
).replace(/\/$/, '')

const oauthProviders = [
  {
    name: 'Google',
    path: '/oauth2/authorization/google',
    image: googleLoginImage,
  },
  {
    name: 'Naver',
    path: '/oauth2/authorization/naver',
    image: naverLoginImage,
  },
  {
    name: 'Kakao',
    path: '/oauth2/authorization/kakao',
    image: kakaoLoginImage,
  },
]

interface LoginPageProps {
  onHome: () => void
  onStartSignup: () => void
}

function LoginPage({ onHome, onStartSignup }: LoginPageProps) {
  return (
    <div className="app-page login-page">
      <AppHeader section="로그인" onHome={onHome}>
        <button className="site-header__help text-action" type="button">
          도움말
        </button>
      </AppHeader>

      <main className="login-content">
        <section className="login-card" aria-labelledby="login-heading">
          <h1 id="login-heading">로그인</h1>
          <p className="login-card__description">
            기사체크 계정으로 저장한 기사와 신고 내역을 확인하세요.
          </p>

          <form
            className="login-form"
            onSubmit={(event) => event.preventDefault()}
          >
            <div className="form-field">
              <label htmlFor="username">아이디</label>
              <input
                id="username"
                name="username"
                type="text"
                autoComplete="username"
                placeholder="아이디 입력"
              />
            </div>

            <div className="form-field">
              <label htmlFor="password">비밀번호</label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                placeholder="비밀번호 입력"
              />
            </div>

            <label className="remember-option">
              <input name="remember-me" type="checkbox" />
              로그인 상태 유지
            </label>

            <button className="primary-button login-form__submit" type="submit">
              로그인
            </button>
          </form>

          <button className="text-action account-recovery" type="button">
            아이디·비밀번호 찾기
          </button>

          <div className="social-divider" aria-hidden="true">
            <span>간편 로그인</span>
          </div>

          <div className="oauth-links" aria-label="소셜 로그인">
            {oauthProviders.map((provider) => (
              <a
                key={provider.name}
                className={`oauth-link oauth-link--${provider.name.toLowerCase()}`}
                href={`${backendBaseUrl}${provider.path}`}
                aria-label={
                  provider.name === 'Google'
                    ? 'Google 계정으로 로그인'
                    : `${provider.name === 'Kakao' ? '카카오' : '네이버'} 로그인`
                }
              >
                <img src={provider.image} alt="" />
              </a>
            ))}
          </div>
        </section>

        <aside className="invitation-card">
          <span className="invitation-card__badge">초대 회원 전용</span>
          <h2>
            처음 방문하셨다면
            <br />
            초대 코드로 시작하세요
          </h2>
          <p className="invitation-card__description">
            기사체크는 현재 초대받은 사용자만
            <br />
            가입할 수 있습니다.
          </p>

          <section
            className="signup-steps"
            aria-labelledby="signup-steps-heading"
          >
            <h3 id="signup-steps-heading">회원가입 순서</h3>
            <ol>
              <li>초대 코드 확인</li>
              <li>계정 정보 입력</li>
              <li>이메일 인증</li>
            </ol>
          </section>

          <button
            className="primary-button signup-button"
            type="button"
            onClick={onStartSignup}
          >
            초대 코드로 회원가입 시작하기
          </button>
          <p className="invitation-card__social-note">
            소셜 계정으로 가입할 때도 초대 코드가 필요합니다.
            <strong>초대 코드를 먼저 준비해 주세요.</strong>
          </p>
        </aside>
      </main>
    </div>
  )
}

export default LoginPage
