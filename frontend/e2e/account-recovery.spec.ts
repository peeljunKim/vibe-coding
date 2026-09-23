// 계정 복구 Browser 회귀 검증
import { expect, test, type Page } from '@playwright/test'

const browserErrors = new WeakMap<Page, string[]>()

test.beforeEach(async ({ page }) => {
  const errors: string[] = []
  browserErrors.set(page, errors)
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('pageerror', (error) => errors.push(error.message))
  await page.route('**/api/auth/session', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ authenticated: false }),
    }),
  )
  await page.route('**/api/csrf', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'set-cookie': 'XSRF-TOKEN=recovery-e2e-csrf; Path=/; SameSite=Lax',
      },
      contentType: 'application/json',
      body: '{}',
    }),
  )
})

test.afterEach(({ page }) => {
  expect(browserErrors.get(page)).toEqual([])
})

test('로그인에서 아이디 찾기 완료까지 이동한다', async ({ page }) => {
  await page.route('**/api/auth/recovery/username/code', (route) =>
    route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        remainingAttempts: 5,
        resendAvailableInSeconds: 60,
      }),
    }),
  )
  await page.route('**/api/auth/recovery/username/verify', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ maskedUsername: 'he***********' }),
    }),
  )

  await page.goto('/login')
  await page.getByRole('button', { name: '아이디·비밀번호 찾기' }).click()
  await expect(page.getByRole('heading', { name: '아이디·비밀번호 찾기' })).toBeVisible()

  await page.getByLabel('가입 이메일').fill('user@example.com')
  await page.getByRole('button', { name: '인증번호 받기' }).click()
  await expect(page.getByRole('status')).toContainText(
    '일치하는 계정이 있으면 인증번호를 보냈습니다',
  )

  await page.getByLabel('6자리 인증번호').fill('482916')
  await page.getByRole('button', { name: '아이디 확인' }).click()

  await expect(page.getByText('he***********')).toBeVisible()
  await expect(page.getByText('전체 아이디는 가입 이메일로 보냈습니다.')).toBeVisible()
})

test('비밀번호 재설정 완료 상태를 표시한다', async ({ page }) => {
  await page.route('**/api/auth/recovery/password/code', (route) =>
    route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        remainingAttempts: 5,
        resendAvailableInSeconds: 60,
      }),
    }),
  )
  await page.route('**/api/auth/recovery/password/reset', (route) =>
    route.fulfill({ status: 204 }),
  )

  await page.goto('/account-recovery')
  await page.getByRole('tab', { name: '비밀번호 재설정' }).click()
  await page.getByLabel('가입 이메일').fill('user@example.com')
  await page.getByRole('button', { name: '인증번호 받기' }).click()
  await page.getByLabel('6자리 인증번호').fill('482916')
  await page.getByLabel('새 비밀번호', { exact: true }).fill('Password!23')
  await page.getByLabel('새 비밀번호 확인').fill('Password!23')
  await page.getByRole('button', { name: '비밀번호 변경' }).click()

  await expect(page.getByText('비밀번호를 변경했습니다.')).toBeVisible()
  await expect(page.getByRole('button', { name: '로그인하기' })).toBeVisible()
})
