// Google OAuth Mock Redirect와 소셜 초대 가입 Browser 검증
import { expect, test, type Page } from '@playwright/test'
import { mockDailyUsage } from './support/daily-usage'

const browserErrors = new WeakMap<Page, string[]>()

test.beforeEach(async ({ page }) => {
  const errors: string[] = []
  browserErrors.set(page, errors)
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('pageerror', (error) => errors.push(error.message))
  await mockDailyUsage(page)
  await page.route('**/api/auth/session', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ authenticated: false }),
    }),
  )
  await page.route('**/api/auth/providers', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ google: true, naver: true, kakao: true }),
    }),
  )
  await page.route('**/api/csrf', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'set-cookie': 'XSRF-TOKEN=oauth-e2e-csrf; Path=/; SameSite=Lax',
      },
      contentType: 'application/json',
      body: '{}',
    }),
  )
})

test.afterEach(({ page }) => {
  expect(browserErrors.get(page)).toEqual([])
})

test('Mock Google 인증 후 초대 코드로 가입을 완료한다', async ({ page }) => {
  await page.route(
    'http://localhost:8080/oauth2/authorization/google',
    (route) =>
      route.fulfill({
        status: 302,
        headers: {
          location: 'http://127.0.0.1:4173/signup/social/invite',
        },
      }),
  )
  await page.route('**/api/signup/social', async (route) => {
    expect(route.request().postDataJSON()).toEqual({
      inviteCode: 'INVITE-2026',
      agreementsAccepted: true,
    })
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        authenticated: true,
        userId: '42',
        role: 'USER',
        expiresInSeconds: 7200,
      }),
    })
  })

  await page.goto('/login')
  await page.getByRole('link', { name: 'Google 계정으로 로그인' }).click()
  await expect(
    page.getByRole('heading', { name: '초대 코드로 가입을 완료해 주세요' }),
  ).toBeVisible()
  await page.getByRole('textbox', { name: '초대 코드' }).fill('INVITE-2026')
  await page
    .getByRole('checkbox', {
      name: '개인정보 처리와 서비스 이용약관에 동의합니다.',
    })
    .check()
  await page.getByRole('button', { name: '가입 완료' }).click()

  await expect(
    page.getByRole('heading', { name: '기사의 주장과 제목을 쉽게 확인해 보세요' }),
  ).toBeVisible()
})

test('OAuth 취소와 실패를 같은 로그인 화면에서 안전하게 안내한다', async ({ page }) => {
  await page.goto('/login?oauth=cancelled')
  await expect(
    page.getByText('로그인이 취소되었습니다. 다시 시도할 수 있습니다.'),
  ).toBeVisible()

  await page.goto('/login?oauth=failed')
  await expect(
    page.getByText('로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.'),
  ).toBeVisible()
})

test('Backend에서 활성화된 Kakao 로그인 시작 링크를 표시한다', async ({ page }) => {
  await page.goto('/login')

  await expect(page.getByRole('link', { name: '카카오 로그인' })).toHaveAttribute(
    'href',
    'http://localhost:8080/oauth2/authorization/kakao',
  )
})
