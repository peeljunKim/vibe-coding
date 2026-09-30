// 도움말 화면 Desktop Browser 검증
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
})

test.afterEach(({ page }) => {
  expect(browserErrors.get(page)).toEqual([])
})

test('로그인 화면에서 도움말을 열고 Desktop 너비를 유지한다', async ({ page }) => {
  for (const width of [1024, 1280, 1440]) {
    await page.setViewportSize({ width, height: 1024 })
    await page.goto('/login')
    await page.getByRole('link', { name: '도움말' }).click()

    await expect(
      page.getByRole('heading', { name: '기사체크를 이렇게 이용하세요' }),
    ).toBeVisible()
    await expect(page.getByText('URL 복사')).toBeVisible()
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)
  }
})
