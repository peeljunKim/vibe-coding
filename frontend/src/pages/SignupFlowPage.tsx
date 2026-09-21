// 회원가입 단계별 화면
import { useEffect, useState } from 'react'
import {
  registerAccount,
  resendSignupCode,
  verifySignupEmail,
} from '../api/signup'
import AppHeader from '../components/AppHeader'
import SignupProgress from '../components/SignupProgress'
import type { SignupVerificationViewData } from '../types/pageData'

interface SignupFlowPageProps {
  onLogin: () => void
  verification?: SignupVerificationViewData
}

function SignupFlowPage({ onLogin, verification }: SignupFlowPageProps) {
  const [step, setStep] = useState<1 | 2 | 3>(1)
  const [account, setAccount] = useState<SignupAccountData>()
  const [verificationState, setVerificationState] =
    useState<SignupVerificationViewData>()
  const [isPending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submitAccount = async (input: SignupAccountInput) => {
    setPending(true)
    setError(null)
    try {
      const pending = await registerAccount(input)
      setAccount({
        userId: pending.userId,
        username: pending.username,
        email: pending.email,
      })
      setVerificationState(pending)
      setStep(2)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : '회원가입을 완료하지 못했습니다. 다시 시도해 주세요.',
      )
    } finally {
      setPending(false)
    }
  }

  const verifyEmail = async (code: string) => {
    if (!account) return
    setPending(true)
    setError(null)
    try {
      await verifySignupEmail(account.userId, code)
      setStep(3)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : '인증번호를 확인하지 못했습니다. 다시 시도해 주세요.',
      )
    } finally {
      setPending(false)
    }
  }

  const resendCode = async (): Promise<number | undefined> => {
    if (!account) return undefined
    setPending(true)
    setError(null)
    try {
      const pending = await resendSignupCode(account.userId)
      setVerificationState(pending)
      return pending.resendAvailableInSeconds
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : '인증번호를 다시 발급하지 못했습니다.',
      )
    } finally {
      setPending(false)
    }
    return undefined
  }

  return (
    <div className="app-page signup-page">
      <AppHeader section="회원가입">
        <button type="button" onClick={onLogin}>
          로그인으로 돌아가기
        </button>
      </AppHeader>

      <main className="signup-content">
        <header className="signup-heading">
          <div>
            <h1>회원가입</h1>
            <p>초대 사용자 계정을 만들고 이메일을 인증합니다.</p>
          </div>
          <SignupProgress currentStep={step} />
        </header>

        {step === 1 ? (
          <SignupAccountStep
            isPending={isPending}
            error={error}
            onNext={submitAccount}
          />
        ) : null}
        {step === 2 ? (
          <SignupVerificationStep
            email={account?.email ?? ''}
            verification={verificationState ?? verification}
            isPending={isPending}
            error={error}
            onNext={verifyEmail}
            onResend={resendCode}
          />
        ) : null}
        {step === 3 ? (
          <SignupCompleteStep account={account} onLogin={onLogin} />
        ) : null}
      </main>
    </div>
  )
}

interface SignupAccountData {
  userId: number
  username: string
  email: string
}

interface SignupAccountInput {
  inviteCode: string
  username: string
  password: string
  passwordConfirm: string
  email: string
  phoneNumber: string
  agreementsAccepted: boolean
}

interface SignupAccountStepProps {
  isPending: boolean
  error: string | null
  onNext: (account: SignupAccountInput) => Promise<void>
}

function SignupAccountStep({
  isPending,
  error,
  onNext,
}: SignupAccountStepProps) {
  return (
    <section
      className="signup-account-card"
      aria-labelledby="signup-account-heading"
    >
      <h2 id="signup-account-heading">
        1. 초대 코드와 계정 정보를 입력해 주세요
      </h2>
      <p>
        모든 항목은 필수입니다. 이메일 인증까지 완료해야 로그인할 수 있습니다.
      </p>

      <aside className="invite-code-guide" role="note">
        <strong>초대 코드</strong>
        <p>가입을 허용받은 사용자에게 전달된 공용 초대 코드입니다.</p>
        <small>서버에 등록된 초대 코드와 일치해야 가입할 수 있습니다.</small>
      </aside>

      <form
        onSubmit={(event) => {
          event.preventDefault()
          const formData = new FormData(event.currentTarget)
          const username = formData.get('signup-username')
          const inviteCode = formData.get('invite-code')
          const password = formData.get('signup-password')
          const passwordConfirm = formData.get('signup-password-confirm')
          const email = formData.get('email')
          const phoneNumber = formData.get('phone')
          void onNext({
            inviteCode: typeof inviteCode === 'string' ? inviteCode : '',
            username: typeof username === 'string' ? username : '',
            password: typeof password === 'string' ? password : '',
            passwordConfirm:
              typeof passwordConfirm === 'string' ? passwordConfirm : '',
            email: typeof email === 'string' ? email : '',
            phoneNumber: typeof phoneNumber === 'string' ? phoneNumber : '',
            agreementsAccepted: formData.get('agreements-accepted') === 'on',
          })
        }}
      >
        <div className="signup-form-grid">
          <label>
            <span>초대 코드</span>
            <input
              name="invite-code"
              placeholder="초대 코드를 입력하세요"
              required
            />
            <small>서버 검증 후 사용 가능 여부 표시</small>
          </label>
          <label>
            <span>사용자 아이디</span>
            <input
              name="signup-username"
              placeholder="영문 소문자와 숫자 포함 5~20자"
              autoComplete="username"
              minLength={5}
              maxLength={20}
              required
            />
            <small>영문 소문자·숫자 포함 5~20자</small>
          </label>
          <label>
            <span>비밀번호</span>
            <input
              name="signup-password"
              type="password"
              placeholder="••••••••••••"
              autoComplete="new-password"
              minLength={10}
              required
            />
            <small>10자 이상, 영문·숫자·특수문자 포함</small>
          </label>
          <label>
            <span>비밀번호 확인</span>
            <input
              name="signup-password-confirm"
              type="password"
              placeholder="••••••••••••"
              autoComplete="new-password"
              minLength={10}
              required
            />
            <small>입력한 비밀번호와 동일한 값</small>
          </label>
          <label>
            <span>이메일</span>
            <input
              name="email"
              type="email"
              placeholder="인증받을 이메일을 입력하세요"
              autoComplete="email"
              required
            />
            <small>다음 단계에서 6자리 인증번호를 받습니다.</small>
          </label>
          <label>
            <span>휴대전화 번호</span>
            <input
              name="phone"
              type="tel"
              placeholder="010-0000-0000"
              autoComplete="tel"
              pattern="010-[0-9]{4}-[0-9]{4}"
              onInput={(event) => {
                const digits = event.currentTarget.value
                  .replace(/[^0-9]/g, '')
                  .slice(0, 11)
                event.currentTarget.value = [
                  digits.slice(0, 3),
                  digits.slice(3, 7),
                  digits.slice(7, 11),
                ]
                  .filter(Boolean)
                  .join('-')
              }}
              required
            />
            <small>SMS 인증은 진행하지 않습니다.</small>
          </label>
        </div>

        <div className="signup-account-footer">
          <label className="signup-agreement">
            <input name="agreements-accepted" type="checkbox" required />
            개인정보 처리와 서비스 이용약관에 동의합니다.
          </label>
          <button className="primary-button" type="submit" disabled={isPending}>
            {isPending ? '계정 생성 중' : '다음: 이메일 인증'}
          </button>
        </div>
        {error ? <p role="alert">{error}</p> : null}
      </form>
    </section>
  )
}

interface SignupVerificationStepProps {
  email: string
  verification: SignupVerificationViewData | undefined
  isPending: boolean
  error: string | null
  onNext: (code: string) => Promise<void>
  onResend: () => Promise<number | undefined>
}

function SignupVerificationStep({
  email,
  verification,
  isPending,
  error,
  onNext,
  onResend,
}: SignupVerificationStepProps) {
  const digitIndexes = [0, 1, 2, 3, 4, 5]
  const [resendRemaining, setResendRemaining] = useState(
    verification?.resendAvailableInSeconds ?? 0,
  )

  useEffect(() => {
    if (resendRemaining <= 0) return
    const timer = window.setTimeout(
      () => setResendRemaining((remaining) => Math.max(remaining - 1, 0)),
      1_000,
    )
    return () => window.clearTimeout(timer)
  }, [resendRemaining])

  const resendMinutes = Math.floor(resendRemaining / 60)
  const resendSeconds = resendRemaining % 60

  return (
    <section
      className="signup-verification-card"
      aria-labelledby="signup-verification-heading"
    >
      <span className="signup-email-badge">이메일 인증 필수</span>
      <h2
        id="signup-verification-heading"
        aria-label={`${email}으로 6자리 인증번호를 보냈습니다`}
      >
        {email}으로
        <br />
        6자리 인증번호를 보냈습니다
      </h2>
      <p>
        24시간 안에 입력해 주세요. 재발송하면 기존 인증번호는 즉시 무효화됩니다.
      </p>

      <div className="verification-layout">
        <form
          onSubmit={(event) => {
            event.preventDefault()
            const formData = new FormData(event.currentTarget)
            const code = digitIndexes
              .map((index) => {
                const digit = formData.get(`verification-digit-${index + 1}`)
                return typeof digit === 'string' ? digit : ''
              })
              .join('')
            void onNext(code)
          }}
        >
          <fieldset>
            <legend>인증번호</legend>
            <div className="verification-code">
              {digitIndexes.map((index) => (
                <input
                  key={index}
                  name={`verification-digit-${index + 1}`}
                  aria-label={`인증번호 ${index + 1}번째 자리`}
                  inputMode="numeric"
                  pattern="[0-9]"
                  maxLength={1}
                  required
                />
              ))}
            </div>
          </fieldset>
          <div className="verification-meta">
            <span>
              {verification
                ? `남은 입력 기회 ${verification.remainingAttempts}회`
                : '[남은 인증 입력 횟수 데이터가 필요합니다.]'}
            </span>
            <strong>
              {verification
                ? `재발송 가능 ${String(resendMinutes).padStart(2, '0')}:${String(resendSeconds).padStart(2, '0')}`
                : '[재발송 가능 시간 데이터가 필요합니다.]'}
            </strong>
          </div>
          <button
            className="primary-button verification-submit"
            type="submit"
            disabled={isPending}
          >
            {isPending ? '인증 확인 중' : '인증번호 확인'}
          </button>
          {error ? <p role="alert">{error}</p> : null}
        </form>

        <aside className="verification-limit" role="note">
          <h3>인증 제한 안내</h3>
          <ul>
            <li>인증번호당 최대 5회 입력</li>
            <li>재발송은 1분 간격</li>
            <li>계정당 하루 최대 5회 발송</li>
            <li>5회 실패 시 30분 동안 차단</li>
          </ul>
          <button
            type="button"
            disabled={isPending || resendRemaining > 0}
            onClick={() => {
              void onResend().then((remaining) => {
                if (remaining !== undefined) setResendRemaining(remaining)
              })
            }}
          >
            인증번호 재발송
          </button>
        </aside>
      </div>

      <aside className="invite-preserved-notice" role="note">
        <strong>초대 코드 검증을 완료했습니다.</strong>
        <p>이메일 인증을 완료하면 계정이 활성화됩니다.</p>
      </aside>
    </section>
  )
}

interface SignupCompleteStepProps extends SignupFlowPageProps {
  account: SignupAccountData | undefined
}

function SignupCompleteStep({ account, onLogin }: SignupCompleteStepProps) {
  return (
    <section
      className="signup-complete-card"
      aria-labelledby="signup-complete-heading"
    >
      <div className="signup-complete-icon" aria-hidden="true">
        ✓
      </div>
      <h2 id="signup-complete-heading">회원가입이 완료되었습니다</h2>
      <p>이메일 인증을 완료해 이제 로그인할 수 있습니다.</p>
      <dl>
        <div>
          <dt>사용자 아이디</dt>
          <dd>{account?.username ?? '[사용자 아이디가 필요합니다.]'}</dd>
        </div>
        <div>
          <dt>인증 이메일</dt>
          <dd>{account?.email ?? '[인증 이메일이 필요합니다.]'}</dd>
        </div>
      </dl>
      <button className="primary-button" type="button" onClick={onLogin}>
        로그인하러 가기
      </button>
      <small>초대 코드 검증 정보가 계정에 반영되었습니다.</small>
    </section>
  )
}

export default SignupFlowPage
