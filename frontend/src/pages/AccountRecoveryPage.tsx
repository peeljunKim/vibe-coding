// 아이디 찾기와 비밀번호 재설정 화면
import { useEffect, useState } from 'react'
import {
  requestRecoveryCode,
  resetRecoveredPassword,
  verifyUsernameRecovery,
  type RecoveryPurpose,
} from '../api/accountRecovery'
import AppHeader from '../components/AppHeader'

interface AccountRecoveryPageProps {
  onHome: () => void
  onLogin: () => void
}

const readFormText = (formData: FormData, name: string) => {
  const value = formData.get(name)
  return typeof value === 'string' ? value : ''
}

function AccountRecoveryPage({ onHome, onLogin }: AccountRecoveryPageProps) {
  const [purpose, setPurpose] = useState<RecoveryPurpose>('username')
  const [email, setEmail] = useState('')
  const [codeRequested, setCodeRequested] = useState(false)
  const [resendSeconds, setResendSeconds] = useState(0)
  const [maskedUsername, setMaskedUsername] = useState<string>()
  const [passwordReset, setPasswordReset] = useState(false)
  const [error, setError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (resendSeconds <= 0) return
    const timer = window.setInterval(
      () => setResendSeconds((remaining) => Math.max(0, remaining - 1)),
      1000,
    )
    return () => window.clearInterval(timer)
  }, [resendSeconds])

  const changePurpose = (nextPurpose: RecoveryPurpose) => {
    setPurpose(nextPurpose)
    setCodeRequested(false)
    setResendSeconds(0)
    setMaskedUsername(undefined)
    setPasswordReset(false)
    setError(undefined)
  }

  const requestCode = async () => {
    setSubmitting(true)
    setError(undefined)
    try {
      const result = await requestRecoveryCode(purpose, email)
      setCodeRequested(true)
      setResendSeconds(result.resendAvailableInSeconds)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : '인증번호를 요청하지 못했습니다.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  const verifyUsername = async (formData: FormData) => {
    const code = readFormText(formData, 'verification-code')
    const result = await verifyUsernameRecovery(email, code)
    setMaskedUsername(result.maskedUsername)
  }

  const resetPassword = async (formData: FormData) => {
    await resetRecoveredPassword({
      email,
      code: readFormText(formData, 'verification-code'),
      password: readFormText(formData, 'new-password'),
      passwordConfirm: readFormText(formData, 'password-confirm'),
    })
    setPasswordReset(true)
  }

  const submitVerification = async (formData: FormData) => {
    setSubmitting(true)
    setError(undefined)
    try {
      if (purpose === 'username') {
        await verifyUsername(formData)
      } else {
        await resetPassword(formData)
      }
    } catch (verificationError) {
      setError(
        verificationError instanceof Error
          ? verificationError.message
          : '계정 복구를 완료하지 못했습니다.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  const completed = maskedUsername !== undefined || passwordReset

  return (
    <div className="app-page account-recovery-page">
      <AppHeader section="계정 복구" onHome={onHome}>
        <button className="text-action" type="button" onClick={onLogin}>
          로그인으로 돌아가기
        </button>
      </AppHeader>

      <main className="account-recovery-content">
        <section className="account-recovery-card" aria-labelledby="recovery-heading">
          <h1 id="recovery-heading">아이디·비밀번호 찾기</h1>
          <p className="account-recovery-card__description">
            가입 이메일로 인증한 뒤 계정 정보를 확인하거나 비밀번호를 변경하세요.
          </p>

          <div className="account-recovery-tabs" role="tablist" aria-label="계정 복구 방식">
            <button
              type="button"
              role="tab"
              aria-selected={purpose === 'username'}
              className={purpose === 'username' ? 'is-active' : undefined}
              onClick={() => changePurpose('username')}
            >
              아이디 찾기
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={purpose === 'password'}
              className={purpose === 'password' ? 'is-active' : undefined}
              onClick={() => changePurpose('password')}
            >
              비밀번호 재설정
            </button>
          </div>

          {!completed && (
            <form
              className="account-recovery-form"
              onSubmit={(event) => {
                event.preventDefault()
                const formData = new FormData(event.currentTarget)
                if (codeRequested) {
                  void submitVerification(formData)
                } else {
                  void requestCode()
                }
              }}
            >
              <div className="form-field">
                <label htmlFor="recovery-email">가입 이메일</label>
                <input
                  id="recovery-email"
                  type="email"
                  autoComplete="email"
                  required
                  maxLength={320}
                  value={email}
                  disabled={codeRequested}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="user@example.com"
                />
              </div>

              {codeRequested && (
                <>
                  <p className="account-recovery-notice" role="status">
                    입력한 이메일과 일치하는 계정이 있으면 인증번호를 보냈습니다.
                  </p>
                  <div className="form-field">
                    <label htmlFor="recovery-code">6자리 인증번호</label>
                    <input
                      id="recovery-code"
                      name="verification-code"
                      inputMode="numeric"
                      autoComplete="one-time-code"
                      pattern="[0-9]{6}"
                      maxLength={6}
                      required
                    />
                  </div>
                  {purpose === 'password' && (
                    <>
                      <div className="form-field">
                        <label htmlFor="new-password">새 비밀번호</label>
                        <input
                          id="new-password"
                          name="new-password"
                          type="password"
                          autoComplete="new-password"
                          minLength={10}
                          required
                        />
                        <small>영문·숫자·특수문자 포함 10자 이상</small>
                      </div>
                      <div className="form-field">
                        <label htmlFor="password-confirm">새 비밀번호 확인</label>
                        <input
                          id="password-confirm"
                          name="password-confirm"
                          type="password"
                          autoComplete="new-password"
                          required
                        />
                      </div>
                    </>
                  )}
                </>
              )}

              {error && <p className="login-form__error" role="alert">{error}</p>}

              <button className="primary-button account-recovery-submit" type="submit" disabled={submitting}>
                {codeRequested
                  ? purpose === 'username'
                    ? '아이디 확인'
                    : '비밀번호 변경'
                  : '인증번호 받기'}
              </button>

              {codeRequested && (
                <button
                  className="text-action account-recovery-resend"
                  type="button"
                  disabled={submitting || resendSeconds > 0}
                  onClick={() => void requestCode()}
                >
                  {resendSeconds > 0 ? `재발송 가능 ${resendSeconds}초` : '인증번호 재발송'}
                </button>
              )}
            </form>
          )}

          {maskedUsername && (
            <div className="account-recovery-result" role="status">
              <span>확인된 아이디</span>
              <strong>{maskedUsername}</strong>
              <p>전체 아이디는 가입 이메일로 보냈습니다.</p>
              <button className="primary-button account-recovery-submit" type="button" onClick={onLogin}>
                로그인하기
              </button>
            </div>
          )}

          {passwordReset && (
            <div className="account-recovery-result" role="status">
              <strong>비밀번호를 변경했습니다.</strong>
              <p>새 비밀번호로 다시 로그인해 주세요.</p>
              <button className="primary-button account-recovery-submit" type="button" onClick={onLogin}>
                로그인하기
              </button>
            </div>
          )}
        </section>
      </main>
    </div>
  )
}

export default AccountRecoveryPage
