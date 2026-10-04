// 실제 Frontend·Backend·MySQL·Redis 연결 Smoke 시나리오
import { expect, test } from '@playwright/test'

const requiredEnvironment = (name: string) => {
  const value = process.env[name]
  if (!value) {
    throw new Error(`${name} is required for the Full-stack Smoke test`)
  }
  return value
}

const username = requiredEnvironment('E2E_USERNAME')
const email = requiredEnvironment('E2E_EMAIL')
const phoneNumber = requiredEnvironment('E2E_PHONE_NUMBER')
const password = requiredEnvironment('E2E_PASSWORD')
const inviteCode = requiredEnvironment('E2E_INVITE_CODE')
const verificationCode = requiredEnvironment('E2E_SIGNUP_CODE')
const publisherName = requiredEnvironment('E2E_PUBLISHER_NAME')
const articleUrl = requiredEnvironment('E2E_ARTICLE_URL')
const articleTitle = requiredEnvironment('E2E_ARTICLE_TITLE')

test('회원가입부터 분석·저장·로그아웃까지 실제 Local 경계를 통과한다', async ({
  page,
}) => {
  await page.goto('/')

  await page.getByRole('button', { name: '지원 언론사 보기' }).click()
  await expect(page.getByText(publisherName, { exact: true })).toBeVisible()

  await page.getByRole('link', { name: '로그인' }).click()
  await page
    .getByRole('button', { name: '초대 코드로 회원가입 시작하기' })
    .click()

  await page.locator('[name="invite-code"]').fill(inviteCode)
  await page.locator('[name="signup-username"]').fill(username)
  await page.locator('[name="signup-password"]').fill(password)
  await page.locator('[name="signup-password-confirm"]').fill(password)
  await page.locator('[name="email"]').fill(email)
  await page.locator('[name="phone"]').fill(phoneNumber)
  await page.locator('[name="agreements-accepted"]').check()
  await page.getByRole('button', { name: '다음: 이메일 인증' }).click()

  for (const [index, digit] of [...verificationCode].entries()) {
    await page.getByLabel(`인증번호 ${index + 1}번째 자리`).fill(digit)
  }
  await page.getByRole('button', { name: '인증번호 확인' }).click()
  await expect(
    page.getByRole('heading', { name: '회원가입이 완료되었습니다' }),
  ).toBeVisible()
  await page.getByRole('button', { name: '로그인하러 가기' }).click()

  await page.getByLabel('아이디').fill(username)
  await page.getByLabel('비밀번호').fill(password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible()

  const csrfRejectedStatus = await page.evaluate(async () => {
    const response = await fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
    })
    return response.status
  })
  expect(csrfRejectedStatus).toBe(403)
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible()

  await page.evaluate(
    async (url) => navigator.clipboard.writeText(url),
    articleUrl,
  )
  await page
    .getByRole('button', { name: '복사한 건강 기사 확인하기' })
    .click()
  await expect(page.getByRole('heading', { name: articleTitle })).toBeVisible({
    timeout: 20_000,
  })
  await page.getByRole('button', { name: '결과 저장' }).click()
  await expect(page.getByText('결과가 저장되었습니다.')).toBeVisible()

  await page.getByRole('button', { name: '새 기사 확인' }).click()
  await page.evaluate(
    async (url) => navigator.clipboard.writeText(url),
    articleUrl,
  )
  await page
    .getByRole('button', { name: '복사한 기사 제목 확인하기' })
    .click()
  await expect(page.getByRole('heading', { name: articleTitle })).toBeVisible({
    timeout: 20_000,
  })
  await expect(page.getByText('문제를 발견하지 않았습니다.')).toBeVisible()

  await page.getByRole('link', { name: '기사체크' }).click()
  await page.getByRole('button', { name: '저장 기록' }).click()
  await expect(page.getByRole('heading', { name: articleTitle })).toBeVisible()

  await page.getByRole('link', { name: '기사체크' }).click()
  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page.getByRole('link', { name: '로그인' })).toBeVisible()

  const protectedStatus = await page.evaluate(async () => {
    const response = await fetch('/api/health-records', {
      credentials: 'include',
    })
    return response.status
  })
  expect(protectedStatus).toBe(401)
})
