// 당일 이용 횟수 Browser 회귀 검증
import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/auth/session', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ authenticated: false }),
    }),
  )
  await page.route('**/api/usage', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        timezone: 'Asia/Seoul',
        resetsAt: '2026-10-05T00:00:00+09:00',
        health: { limit: 2, used: 1, remaining: 1 },
        headline: { limit: 5, used: 2, remaining: 3 },
      }),
    }),
  )
})

test('Desktop 너비에서 실제 이용 횟수를 표시한다', async ({ page }) => {
  for (const width of [1024, 1280, 1440]) {
    await page.setViewportSize({ width, height: 1024 })
    await page.goto('/')

    await expect(
      page.getByText('오늘 남은 횟수 건강 1 · 제목 3'),
    ).toBeVisible()
    await expect(
      page.getByRole('button', { name: '복사한 건강 기사 확인하기' }),
    ).toBeEnabled()
    await expect(
      page.getByRole('button', { name: '복사한 기사 제목 확인하기' }),
    ).toBeEnabled()
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true)
  }
})
