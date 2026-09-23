// 이메일 기반 계정 복구 API
export type RecoveryPurpose = 'username' | 'password'

export interface RecoveryRequestResponse {
  remainingAttempts: number
  resendAvailableInSeconds: number
}

export interface RecoveredUsernameResponse {
  maskedUsername: string
}

const ERROR_MESSAGES: Record<string, string> = {
  INVALID_RECOVERY_REQUEST: '입력 내용을 확인해 주세요.',
  INVALID_VERIFICATION_CODE: '인증번호를 확인해 주세요.',
  VERIFICATION_EXPIRED: '인증번호가 만료되었습니다. 다시 발급해 주세요.',
  VERIFICATION_BLOCKED:
    '인증이 일시 제한되었습니다. 30분 후 다시 시도해 주세요.',
  RESEND_TOO_SOON: '인증번호는 1분 후 다시 발급할 수 있습니다.',
  DAILY_SEND_LIMIT_EXCEEDED:
    '오늘 가능한 인증번호 발송 횟수를 모두 사용했습니다.',
  PASSWORD_CONFIRMATION_MISMATCH: '비밀번호 확인 값이 일치하지 않습니다.',
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
    throw new Error('계정 복구 요청을 시작하지 못했습니다. 다시 시도해 주세요.')
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
        '계정 복구를 완료하지 못했습니다. 다시 시도해 주세요.',
    )
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export const requestRecoveryCode = (
  purpose: RecoveryPurpose,
  email: string,
) =>
  post<RecoveryRequestResponse>(`/api/auth/recovery/${purpose}/code`, {
    email,
  })

export const verifyUsernameRecovery = (email: string, code: string) =>
  post<RecoveredUsernameResponse>('/api/auth/recovery/username/verify', {
    email,
    code,
  })

export const resetRecoveredPassword = (request: {
  email: string
  code: string
  password: string
  passwordConfirm: string
}) => post<void>('/api/auth/recovery/password/reset', request)
