// 일반 회원가입과 이메일 인증 Browser 회귀 검증
import { expect, test, type Page } from '@playwright/test'

const browserErrors = new WeakMap<Page, string[]>()

test.beforeEach(async ({ page }) => {
  const errors: string[] = []
  browserErrors.set(page, errors)
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('pageerror', (error) => errors.push(error.message))
  await page.route('**/api/csrf', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'set-cookie': 'XSRF-TOKEN=signup-e2e-csrf; Path=/; SameSite=Lax',
      },
      contentType: 'application/json',
      body: '{}',
    }),
  )
})

test.afterEach(({ page }) => {
  expect(browserErrors.get(page)).toEqual([])
})

test('계정 입력부터 이메일 인증 완료까지 이동한다', async ({ page }) => {
  let registrationBody: Record<string, unknown> | undefined
  await page.route('**/api/signup/email-verification', async (route) => {
    const requestBody = route.request().postDataJSON() as Record<
      string,
      unknown
    >
    expect(requestBody).toEqual({ userId: 42, code: '482916' })
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        userId: 42,
        username: 'healthcheck26',
        email: 'user@example.com',
      }),
    })
  })
  await page.route('**/api/signup', async (route) => {
    registrationBody = route.request().postDataJSON() as Record<string, unknown>
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        userId: 42,
        username: 'healthcheck26',
        email: 'user@example.com',
        remainingAttempts: 5,
        resendAvailableInSeconds: 60,
      }),
    })
  })

  await page.goto('/login')
  await page
    .getByRole('button', { name: '초대 코드로 회원가입 시작하기' })
    .click()
  await page.locator('[name="invite-code"]').fill('INVITE-2026')
  await page.locator('[name="signup-username"]').fill('healthcheck26')
  await page.locator('[name="signup-password"]').fill('Password!23')
  await page.locator('[name="signup-password-confirm"]').fill('Password!23')
  await page.locator('[name="email"]').fill('user@example.com')
  await page.locator('[name="phone"]').fill('01012345678')
  await expect(page.locator('[name="phone"]')).toHaveValue('010-1234-5678')
  await page
    .getByRole('checkbox', {
      name: '개인정보 처리와 서비스 이용약관에 동의합니다.',
    })
    .check()
  await page.getByRole('button', { name: '다음: 이메일 인증' }).click()

  await expect(
    page.getByRole('heading', {
      name: 'user@example.com으로 6자리 인증번호를 보냈습니다',
    }),
  ).toBeVisible()
  expect(registrationBody).toMatchObject({
    username: 'healthcheck26',
    email: 'user@example.com',
    phoneNumber: '010-1234-5678',
    agreementsAccepted: true,
  })

  for (const [index, digit] of ['4', '8', '2', '9', '1', '6'].entries()) {
    await page.getByLabel(`인증번호 ${index + 1}번째 자리`).fill(digit)
  }
  await page.getByRole('button', { name: '인증번호 확인' }).click()

  await expect(
    page.getByRole('heading', { name: '회원가입이 완료되었습니다' }),
  ).toBeVisible()
  await expect(page.getByText('healthcheck26')).toBeVisible()
  await expect(page.getByText('user@example.com')).toBeVisible()
})
