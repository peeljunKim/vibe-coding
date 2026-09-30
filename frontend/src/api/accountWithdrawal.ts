// 회원 탈퇴 신청 API
export interface AccountWithdrawalSchedule {
  recoveryDeadline: string
  scheduledDeletionAt: string
}

const readCookie = (name: string) => {
  const prefix = `${name}=`
  const cookie = document.cookie
    .split(';')
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix))
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
}

const csrfToken = async () => {
  const response = await fetch('/api/csrf', { credentials: 'include' })
  const token = readCookie('XSRF-TOKEN')
  if (!response.ok || !token) {
    throw new Error('탈퇴 신청을 시작하지 못했습니다. 다시 시도해 주세요.')
  }
  return token
}

export const requestAccountWithdrawal = async (): Promise<AccountWithdrawalSchedule> => {
  const token = await csrfToken()
  const response = await fetch('/api/account/withdrawal', {
    method: 'POST',
    credentials: 'include',
    headers: { 'X-XSRF-TOKEN': token },
  })
  if (!response.ok) {
    throw new Error('탈퇴를 신청하지 못했습니다. 다시 시도해 주세요.')
  }
  return (await response.json()) as AccountWithdrawalSchedule
}
