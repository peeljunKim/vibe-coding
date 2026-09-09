// 회원가입 단계별 화면
import { useState } from 'react'
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
            onNext={(nextAccount) => {
              setAccount(nextAccount)
              setStep(2)
            }}
          />
        ) : null}
        {step === 2 ? (
          <SignupVerificationStep
            email={account?.email ?? ''}
            verification={verification}
            onNext={() => setStep(3)}
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
  username: string
  email: string
}

interface SignupAccountStepProps {
  onNext: (account: SignupAccountData) => void
}

function SignupAccountStep({ onNext }: SignupAccountStepProps) {
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
        <p>가입을 허용받은 사용자에게 전달된 일회용 코드입니다.</p>
        <small>
          가입이 최종 완료될 때만 사용 처리되며, 중단하거나 실패하면 소모되지
          않습니다.
        </small>
      </aside>

      <form
        onSubmit={(event) => {
          event.preventDefault()
          const formData = new FormData(event.currentTarget)
          const username = formData.get('signup-username')
          const email = formData.get('email')
          onNext({
            username: typeof username === 'string' ? username : '',
            email: typeof email === 'string' ? email : '',
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
              required
            />
            <small>SMS 인증은 진행하지 않습니다.</small>
          </label>
        </div>

        <div className="signup-account-footer">
          <label className="signup-agreement">
            <input type="checkbox" required />
            개인정보 처리와 서비스 이용약관에 동의합니다.
          </label>
          <button className="primary-button" type="submit">
            다음: 이메일 인증
          </button>
        </div>
      </form>
    </section>
  )
}

interface SignupVerificationStepProps {
  email: string
  verification: SignupVerificationViewData | undefined
  onNext: () => void
}

function SignupVerificationStep({
  email,
  verification,
  onNext,
}: SignupVerificationStepProps) {
  const digitIndexes = [0, 1, 2, 3, 4, 5]
  const resendMinutes = verification
    ? Math.floor(verification.resendAvailableInSeconds / 60)
    : 0
  const resendSeconds = verification
    ? verification.resendAvailableInSeconds % 60
    : 0

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
            onNext()
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
          <button className="primary-button verification-submit" type="submit">
            인증번호 확인
          </button>
        </form>

        <aside className="verification-limit" role="note">
          <h3>인증 제한 안내</h3>
          <ul>
            <li>인증번호당 최대 5회 입력</li>
            <li>재발송은 1분 간격</li>
            <li>계정당 하루 최대 5회 발송</li>
            <li>5회 실패 시 30분 동안 차단</li>
          </ul>
          <button type="button" disabled>
            인증번호 재발송
          </button>
        </aside>
      </div>

      <aside className="invite-preserved-notice" role="note">
        <strong>초대 코드가 아직 사용 처리되지 않았습니다.</strong>
        <p>
          이메일 인증과 계정 생성을 모두 완료한 뒤에만 초대 코드가 소모됩니다.
        </p>
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
      <small>초대 코드는 계정 생성 완료 시점에 사용 처리되었습니다.</small>
    </section>
  )
}

export default SignupFlowPage
