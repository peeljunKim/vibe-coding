// 회원가입 단계 표시
interface SignupProgressProps {
  currentStep: 1 | 2 | 3
}

function SignupProgress({ currentStep }: SignupProgressProps) {
  const labels = ['초대·계정 정보', '이메일 인증', '가입 완료']

  return (
    <ol className="signup-progress" aria-label="회원가입 진행 단계">
      {labels.map((label, index) => {
        const step = (index + 1) as 1 | 2 | 3
        const state =
          step === currentStep
            ? 'current'
            : step < currentStep
              ? 'complete'
              : 'waiting'

        return (
          <li
            className={`signup-progress__step signup-progress__step--${state}`}
            key={label}
          >
            <span>{step}단계</span>
            {step === currentStep ? <small>{label}</small> : null}
          </li>
        )
      })}
    </ol>
  )
}

export default SignupProgress
