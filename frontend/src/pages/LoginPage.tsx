// 사용자 로그인 화면
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  getOAuthProviders,
  login,
  type LoginResponse,
  type OAuthProviderAvailability,
} from '../api/auth'
import AppHeader from '../components/AppHeader'
import googleLoginImage from '../assets/oauth/google-login.png'
import kakaoLoginImage from '../assets/oauth/kakao-login.png'
import naverLoginImage from '../assets/oauth/naver-login.png'

const backendBaseUrl = (
  import.meta.env.VITE_BACKEND_BASE_URL ?? 'http://localhost:8080'
).replace(/\/$/, '')

const oauthProviders = [
  {
    key: 'google',
    name: 'Google',
    path: '/oauth2/authorization/google',
    image: googleLoginImage,
  },
  {
    key: 'naver',
    name: 'Naver',
    path: '/oauth2/authorization/naver',
    image: naverLoginImage,
  },
  {
    key: 'kakao',
    name: 'Kakao',
    path: '/oauth2/authorization/kakao',
    image: kakaoLoginImage,
  },
] as const

const disabledOAuthProviders: OAuthProviderAvailability = {
  google: false,
  naver: false,
  kakao: false,
}

const OAUTH_MESSAGES: Record<string, string> = {
  cancelled: '로그인이 취소되었습니다. 다시 시도할 수 있습니다.',
  failed: '로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  'email-required':
    '소셜 계정에서 이메일 제공에 동의한 뒤 다시 시도해 주세요.',
  'existing-account':
    '이미 가입된 이메일입니다. 기존 로그인 방식으로 로그인해 주세요.',
}

interface LoginPageProps {
  onHome: () => void
  onStartSignup: () => void
  onStartRecovery: () => void
  onAuthenticated: (session: LoginResponse) => void
}

function LoginPage({
  onHome,
  onStartSignup,
  onStartRecovery,
  onAuthenticated,
}: LoginPageProps) {
  const [searchParams] = useSearchParams()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setSubmitting] = useState(false)
  const [recoveryDeadline, setRecoveryDeadline] = useState<string | null>(null)
  const [enabledOAuthProviders, setEnabledOAuthProviders] = useState(
    disabledOAuthProviders,
  )
  const oauthMessage = OAUTH_MESSAGES[searchParams.get('oauth') ?? '']

  useEffect(() => {
    let active = true
    void getOAuthProviders()
      .then((providers) => {
        if (active) setEnabledOAuthProviders(providers)
      })
      .catch(() => {
        if (active) setEnabledOAuthProviders(disabledOAuthProviders)
      })
    return () => {
      active = false
    }
  }, [])

  const submitLogin = async (cancelWithdrawal = false) => {
    setSubmitting(true)
    setError(null)
    try {
      const session = await login({
        username,
        password,
        rememberMe,
        cancelWithdrawal,
      })
      setRecoveryDeadline(null)
      onAuthenticated(session)
    } catch (submitError) {
      if (
        submitError instanceof Error &&
        'code' in submitError &&
        submitError.code === 'WITHDRAWAL_RECOVERY_REQUIRED'
      ) {
        setRecoveryDeadline(
          'recoveryDeadline' in submitError &&
            typeof submitError.recoveryDeadline === 'string'
            ? submitError.recoveryDeadline
            : '',
        )
        return
      }
      setError(
        submitError instanceof Error
          ? submitError.message
          : '로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="app-page login-page">
      <AppHeader section="로그인" onHome={onHome}>
        <Link className="site-header__help text-action" to="/help">
          도움말
        </Link>
      </AppHeader>

      <main className="login-content">
        <section className="login-card" aria-labelledby="login-heading">
          <h1 id="login-heading">로그인</h1>
          <p className="login-card__description">
            기사체크 계정으로 저장한 기사와 신고 내역을 확인하세요.
          </p>

          {oauthMessage ? (
            <p className="login-form__error" role="status">
              {oauthMessage}
            </p>
          ) : null}

          <form
            className="login-form"
            onSubmit={(event) => {
              event.preventDefault()
              void submitLogin()
            }}
          >
            <div className="form-field">
              <label htmlFor="username">아이디</label>
              <input
                id="username"
                name="username"
                type="text"
                autoComplete="username"
                placeholder="아이디 입력"
                required
                maxLength={20}
                value={username}
                onChange={(event) => setUsername(event.target.value)}
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
                required
                maxLength={128}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>

            <label className="remember-option">
              <input
                name="remember-me"
                type="checkbox"
                checked={rememberMe}
                onChange={(event) => setRememberMe(event.target.checked)}
              />
              로그인 상태 유지
            </label>
            <p className="remember-notice">
              공용 PC에서는 로그인 상태 유지를 선택하지 마세요.
            </p>

            {error && (
              <p className="login-form__error" role="alert">
                {error}
              </p>
            )}

            <button
              className="primary-button login-form__submit"
              type="submit"
              disabled={isSubmitting}
            >
              {isSubmitting ? '로그인 중' : '로그인'}
            </button>
          </form>

          <button
            className="text-action account-recovery"
            type="button"
            onClick={onStartRecovery}
          >
            아이디·비밀번호 찾기
          </button>

          <div className="social-divider" aria-hidden="true">
            <span>간편 로그인</span>
          </div>

          <div className="oauth-links" aria-label="소셜 로그인">
            {oauthProviders.map((provider) => {
              const enabled = enabledOAuthProviders[provider.key]
              const label =
                provider.name === 'Google'
                  ? 'Google 계정으로 로그인'
                  : `${provider.name === 'Kakao' ? '카카오' : '네이버'} 로그인`
              return enabled ? (
                <a
                  key={provider.name}
                  className={`oauth-link oauth-link--${provider.name.toLowerCase()}`}
                  href={`${backendBaseUrl}${provider.path}`}
                  aria-label={label}
                >
                  <img src={provider.image} alt="" />
                </a>
              ) : (
                <span
                  key={provider.name}
                  className={`oauth-link oauth-link--disabled oauth-link--${provider.name.toLowerCase()}`}
                  aria-label={`${label} 준비 중`}
                  aria-disabled="true"
                >
                  <img src={provider.image} alt="" />
                </span>
              )
            })}
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

      {recoveryDeadline !== null ? (
        <div className="report-dialog-backdrop" role="presentation">
          <section
            className="report-dialog withdrawal-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="withdrawal-recovery-heading"
          >
            <h2 id="withdrawal-recovery-heading">탈퇴를 취소하시겠어요?</h2>
            <p>
              이 계정은 탈퇴 대기 중입니다. 계정을 복구하면 저장된 기록과
              설정을 다시 이용할 수 있습니다.
            </p>
            {recoveryDeadline ? (
              <p className="withdrawal-dialog__deadline">
                복구 가능 기한{' '}
                {new Date(recoveryDeadline).toLocaleDateString('ko-KR', {
                  timeZone: 'Asia/Seoul',
                })}
              </p>
            ) : null}
            <div className="report-dialog__actions">
              <button
                type="button"
                onClick={() => {
                  setRecoveryDeadline(null)
                  setPassword('')
                }}
              >
                탈퇴 유지
              </button>
              <button
                className="primary-button"
                type="button"
                disabled={isSubmitting}
                onClick={() => void submitLogin(true)}
              >
                계정 복구 후 로그인
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </div>
  )
}

export default LoginPage
