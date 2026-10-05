// 소셜 초대 가입 API
import type { SessionState } from './auth'

export interface SocialSignupRequest {
  inviteCode: string
  agreementsAccepted: boolean
}

const ERROR_MESSAGES: Record<string, string> = {
  INVALID_INVITE_CODE: '초대 코드를 확인해 주세요.',
  AGREEMENTS_REQUIRED: '개인정보 처리와 서비스 이용약관 동의가 필요합니다.',
  SOCIAL_SIGNUP_NOT_FOUND:
    '소셜 인증 정보가 만료되었습니다. 다시 로그인해 주세요.',
  SOCIAL_EMAIL_ALREADY_REGISTERED:
    '이미 가입된 이메일입니다. 기존 로그인 방식을 이용해 주세요.',
  SOCIAL_ACCOUNT_CONFLICT:
    '이미 사용 중인 계정 정보가 있습니다. 기존 로그인 방식을 이용해 주세요.',
}

const readCookie = (name: string) => {
  const prefix = `${name}=`
  const cookie = document.cookie
    .split(';')
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix))
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
}

const refreshCsrfToken = async () => {
  const response = await fetch('/api/csrf', { credentials: 'include' })
  const token = readCookie('XSRF-TOKEN')
  if (!response.ok || !token) {
    throw new Error('회원가입 요청을 시작하지 못했습니다. 다시 시도해 주세요.')
  }
  return token
}

export const completeSocialSignup = async (
  request: SocialSignupRequest,
): Promise<SessionState> => {
  const csrfToken = await refreshCsrfToken()
  const response = await fetch('/api/signup/social', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as {
      code?: string
    }
    throw new Error(
      (problem.code ? ERROR_MESSAGES[problem.code] : undefined) ??
        '소셜 회원가입을 완료하지 못했습니다. 다시 시도해 주세요.',
    )
  }
  const session = (await response.json()) as SessionState
  try {
    await refreshCsrfToken()
  } catch {
    // 완료된 인증 상태 변경 우선
  }
  return session
}
