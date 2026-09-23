// 일반 로그인과 Session API
export interface SessionState {
  authenticated: boolean
  userId?: string
  role?: string
  expiresInSeconds?: number
}

export interface LoginResponse extends SessionState {
  username: string
}

export interface LoginRequest {
  username: string
  password: string
  rememberMe: boolean
}

const ERROR_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: '아이디 또는 비밀번호를 확인해 주세요.',
  INVALID_LOGIN_REQUEST: '아이디와 비밀번호를 입력해 주세요.',
  LOGIN_LOCKED: '로그인이 일시 제한되었습니다. 30분 후 이용 가능합니다.',
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
    throw new Error('인증 요청을 시작하지 못했습니다. 다시 시도해 주세요.')
  }
  return token
}

const refreshCsrfTokenAfterAuthChange = async () => {
  try {
    await refreshCsrfToken()
  } catch {
    // 완료된 인증 상태 변경 우선
  }
}

const parseError = async (response: Response, fallback: string) => {
  const problem = (await response.json().catch(() => ({}))) as {
    code?: string
  }
  return (problem.code ? ERROR_MESSAGES[problem.code] : undefined) ?? fallback
}

export const login = async (request: LoginRequest): Promise<LoginResponse> => {
  const csrfToken = await refreshCsrfToken()
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    throw new Error(
      await parseError(
        response,
        '로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      ),
    )
  }
  const authenticated = (await response.json()) as LoginResponse
  await refreshCsrfTokenAfterAuthChange()
  return authenticated
}

export const getSession = async (): Promise<SessionState> => {
  const response = await fetch('/api/auth/session', {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    throw new Error('로그인 상태를 확인하지 못했습니다.')
  }
  return (await response.json()) as SessionState
}

export const logout = async () => {
  const csrfToken = await refreshCsrfToken()
  const response = await fetch('/api/auth/logout', {
    method: 'POST',
    credentials: 'include',
    headers: { 'X-XSRF-TOKEN': csrfToken },
  })
  if (!response.ok) {
    throw new Error('로그아웃하지 못했습니다. 다시 시도해 주세요.')
  }
  await refreshCsrfTokenAfterAuthChange()
}
