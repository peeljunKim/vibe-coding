// 회원 탈퇴 일정과 확인 흐름 Browser 검증
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
      body: JSON.stringify({ authenticated: true, userId: '42', role: 'USER' }),
    }),
  )
  await page.route('**/api/csrf', (route) =>
    route.fulfill({
      status: 204,
      headers: {
        'set-cookie': 'XSRF-TOKEN=withdrawal-e2e-csrf; Path=/; SameSite=Lax',
      },
    }),
  )
})

test.afterEach(({ page }) => {
  expect(browserErrors.get(page)).toEqual([])
})

test('데스크톱 너비에서 탈퇴 일정이 가로로 넘치지 않는다', async ({ page }) => {
  for (const width of [1024, 1280, 1440]) {
    await page.setViewportSize({ width, height: 1024 })
    await page.goto('/settings/account')

    await expect(page.getByRole('heading', { name: '계정 설정' })).toBeVisible()
    await expect(page.getByText('7일 이내')).toBeVisible()
    await expect(page.getByText('탈퇴 신청 후 30일')).toBeVisible()
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)
  }
})

test('확인 뒤 탈퇴 일정을 표시한다', async ({ page }) => {
  await page.route('**/api/account/withdrawal', (route) =>
    route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        recoveryDeadline: '2026-10-06T00:00:00Z',
        scheduledDeletionAt: '2026-10-29T00:00:00Z',
      }),
    }),
  )

  await page.goto('/settings/account')
  await page.getByRole('button', { name: '회원 탈퇴 신청' }).click()
  await expect(page.getByRole('dialog')).toContainText('7일 안에는 로그인하여 복구')
  await page.getByRole('button', { name: '탈퇴 신청 확정' }).click()

  await expect(page.getByRole('heading', { name: '탈퇴 신청이 완료되었습니다' })).toBeVisible()
  await expect(page.getByText(/2026\. 10\. 06\./)).toBeVisible()
  await expect(page.getByText(/2026\. 10\. 29\./)).toBeVisible()
})
