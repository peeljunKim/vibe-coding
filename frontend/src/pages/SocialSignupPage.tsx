// 소셜 초대 가입 화면
import { useState } from 'react'
import type { SessionState } from '../api/auth'
import { completeSocialSignup } from '../api/socialSignup'
import AppHeader from '../components/AppHeader'

interface SocialSignupPageProps {
  onLogin: () => void
  onAuthenticated: (session: SessionState) => void
}

function SocialSignupPage({
  onLogin,
  onAuthenticated,
}: SocialSignupPageProps) {
  const [isPending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  return (
    <div className="app-page signup-page">
      <AppHeader section="소셜 회원가입">
        <button type="button" onClick={onLogin}>
          로그인으로 돌아가기
        </button>
      </AppHeader>

      <main className="signup-content social-signup-content">
        <header className="signup-heading">
          <div>
            <h1>소셜 회원가입</h1>
            <p>인증된 소셜 계정에 초대 코드를 연결합니다.</p>
          </div>
        </header>

        <section
          className="signup-account-card social-signup-card"
          aria-labelledby="social-signup-heading"
        >
          <h2 id="social-signup-heading">초대 코드로 가입을 완료해 주세요</h2>
          <p>
            소셜 계정의 이메일 인증을 사용하므로 아이디, 비밀번호와
            휴대전화 번호를 다시 입력하지 않습니다.
          </p>

          <aside className="invite-code-guide" role="note">
            <strong>초대 회원 전용</strong>
            <p>가입을 허용받은 사용자에게 전달된 초대 코드를 입력해 주세요.</p>
            <small>
              가입 완료 후에는 같은 소셜 계정으로만 로그인할 수 있으며,
              해당 계정을 사용할 수 없으면 기사체크에도 로그인할 수 없습니다.
            </small>
          </aside>

          <form
            className="social-signup-form"
            onSubmit={(event) => {
              event.preventDefault()
              const formData = new FormData(event.currentTarget)
              const inviteCode = formData.get('invite-code')
              setPending(true)
              setError(null)
              void completeSocialSignup({
                inviteCode: typeof inviteCode === 'string' ? inviteCode : '',
                agreementsAccepted:
                  formData.get('agreements-accepted') === 'on',
              })
                .then(onAuthenticated)
                .catch((requestError: unknown) => {
                  setError(
                    requestError instanceof Error
                      ? requestError.message
                      : '소셜 회원가입을 완료하지 못했습니다. 다시 시도해 주세요.',
                  )
                })
                .finally(() => setPending(false))
            }}
          >
            <label className="social-signup-form__field" htmlFor="social-invite-code">
              <span>초대 코드</span>
              <input
                id="social-invite-code"
                name="invite-code"
                placeholder="초대 코드를 입력하세요"
                maxLength={100}
                required
              />
            </label>

            <label className="signup-agreement">
              <input name="agreements-accepted" type="checkbox" required />
              개인정보 처리와 서비스 이용약관에 동의합니다.
            </label>

            {error ? (
              <p className="login-form__error" role="alert">
                {error}
              </p>
            ) : null}

            <button
              className="primary-button social-signup-form__submit"
              type="submit"
              disabled={isPending}
            >
              {isPending ? '가입 처리 중' : '가입 완료'}
            </button>
          </form>
        </section>
      </main>
    </div>
  )
}

export default SocialSignupPage
