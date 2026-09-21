// 일반 회원가입과 이메일 인증 API
export interface SignupRequest {
  inviteCode: string
  username: string
  password: string
  passwordConfirm: string
  email: string
  phoneNumber: string
  agreementsAccepted: boolean
}

export interface PendingSignupResponse {
  userId: number
  username: string
  email: string
  remainingAttempts: number
  resendAvailableInSeconds: number
}

export interface CompletedSignupResponse {
  userId: number
  username: string
  email: string
}

const ERROR_MESSAGES: Record<string, string> = {
  INVALID_INVITE_CODE: '초대 코드를 확인해 주세요.',
  PASSWORD_CONFIRMATION_MISMATCH: '비밀번호 확인 값이 일치하지 않습니다.',
  AGREEMENTS_REQUIRED: '개인정보 처리와 서비스 이용약관 동의가 필요합니다.',
  USERNAME_ALREADY_EXISTS: '이미 사용 중인 사용자 아이디입니다.',
  EMAIL_ALREADY_EXISTS: '이미 등록된 이메일입니다.',
  PHONE_ALREADY_EXISTS: '이미 등록된 번호입니다.',
  DUPLICATE_ACCOUNT: '이미 등록된 계정 정보입니다.',
  INVALID_VERIFICATION_CODE: '인증번호를 확인해 주세요.',
  VERIFICATION_EXPIRED: '인증번호가 만료되었습니다. 다시 발급해 주세요.',
  VERIFICATION_BLOCKED:
    '인증이 일시 제한되었습니다. 30분 후 다시 시도해 주세요.',
  RESEND_TOO_SOON: '인증번호는 1분 후 다시 발급할 수 있습니다.',
  DAILY_SEND_LIMIT_EXCEEDED:
    '오늘 가능한 인증번호 발송 횟수를 모두 사용했습니다.',
}

const readCookie = (name: string) => {
  const prefix = `${name}=`
  const cookie = document.cookie
    .split(';')
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix))
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
}

const post = async <T>(path: string, body: unknown): Promise<T> => {
  const csrfResponse = await fetch('/api/csrf', { credentials: 'include' })
  const csrfToken = readCookie('XSRF-TOKEN')
  if (!csrfResponse.ok || !csrfToken) {
    throw new Error('회원가입 요청을 시작하지 못했습니다. 다시 시도해 주세요.')
  }

  const response = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as {
      code?: string
    }
    throw new Error(
      (problem.code ? ERROR_MESSAGES[problem.code] : undefined) ??
        '회원가입을 완료하지 못했습니다. 다시 시도해 주세요.',
    )
  }
  return (await response.json()) as T
}

export const registerAccount = (request: SignupRequest) =>
  post<PendingSignupResponse>('/api/signup', request)

export const verifySignupEmail = (userId: number, code: string) =>
  post<CompletedSignupResponse>('/api/signup/email-verification', {
    userId,
    code,
  })

export const resendSignupCode = (userId: number) =>
  post<PendingSignupResponse>('/api/signup/email-verification/resend', {
    userId,
  })
