// 공통 당일 이용 횟수 응답
import type { Page } from '@playwright/test'

export const mockDailyUsage = async (page: Page) => {
  await page.route('**/api/usage', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        timezone: 'Asia/Seoul',
        resetsAt: '2026-10-05T00:00:00+09:00',
        health: { limit: 2, used: 0, remaining: 2 },
        headline: { limit: 5, used: 0, remaining: 5 },
      }),
    }),
  )
}
