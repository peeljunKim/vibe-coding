// 분석 결과 공유 Browser 흐름 검증
import { expect, test, type Page } from '@playwright/test'
import { mockDailyUsage } from './support/daily-usage'

const ARTICLE_URL = 'https://news.example.com/share-article'
const SHARE_TOKEN = 'health-share-browser-token'
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
      body: JSON.stringify({
        authenticated: true,
        userId: '42',
        role: 'USER',
        expiresInSeconds: 7200,
      }),
    }),
  )
  await page.route('**/api/csrf', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'set-cookie': 'XSRF-TOKEN=share-e2e-csrf; Path=/; SameSite=Lax',
      },
      contentType: 'application/json',
      body: '{}',
    }),
  )
})

test.afterEach(({ page }) => {
  expect(browserErrors.get(page)).toEqual([])
})

test('건강 결과를 저장 후 공유하고 공개 링크로 조회한다', async ({
  page,
}) => {
  await page.route('**/api/analyses/health', (route) =>
    route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        analysisId: 'health-share-e2e',
        deadlineAt: '2099-09-20T00:01:30Z',
        pollAfterSeconds: 0,
      }),
    }),
  )
  await page.route('**/api/analyses/health/health-share-e2e', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        analysisId: 'health-share-e2e',
        status: 'COMPLETED',
        stage: 'COMPLETED',
        result: healthResult(),
      }),
    }),
  )
  await page.route('**/api/health-records', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 31,
        title: '공유할 건강 기사',
        overallStatus: 'CAUTION',
        analyzedAt: '2026-09-28T01:00:00Z',
        expiresAt: '2026-10-28T01:00:00Z',
      }),
    }),
  )
  await page.route('**/api/health-records/31/shares', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        shareType: 'HEALTH',
        shareToken: SHARE_TOKEN,
        expiresAt: '2026-10-05T01:00:00Z',
      }),
    }),
  )
  await routePublicHealth(page)

  await page.goto('/')
  await page.evaluate((url) => navigator.clipboard.writeText(url), ARTICLE_URL)
  await page.getByRole('button', { name: '복사한 건강 기사 확인하기' }).click()
  await expect(
    page.getByRole('heading', { name: '공유된 핵심 주장' }),
  ).toBeVisible()
  await page.getByRole('button', { name: '결과 공유' }).click()
  await page.getByRole('button', { name: '저장 후 공유' }).click()
  await expect(page.getByLabel('공유 링크')).toHaveValue(
    `http://127.0.0.1:4173/share/health#${SHARE_TOKEN}`,
  )

  await page.goto(`/share/health#${SHARE_TOKEN}`)
  await expect(
    page.getByRole('heading', { name: '공유할 건강 기사' }),
  ).toBeVisible()
  await expect(page.getByText('로그인 후 신고할 수 있습니다.')).toBeVisible()
})

for (const width of [1024, 1280, 1440]) {
  test(`${width}px 공개 공유 화면에서 가로 넘침이 없다`, async ({ page }) => {
    await routePublicHealth(page)
    await page.setViewportSize({ width, height: 900 })
    await page.goto(`/share/health#${SHARE_TOKEN}`)
    await expect(
      page.getByRole('heading', { name: '공유할 건강 기사' }),
    ).toBeVisible()
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true)
  })
}

async function routePublicHealth(page: Page) {
  await page.route('**/api/shares/health', (route) => {
    expect(route.request().headers()['x-share-token']).toBe(SHARE_TOKEN)
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        shareType: 'HEALTH',
        expiresAt: '2026-10-05T01:00:00Z',
        ...healthResult(),
      }),
    })
  })
}

function healthResult() {
  return {
    article: {
      url: ARTICLE_URL,
      title: '공유할 건강 기사',
      publisher: '테스트 언론사',
    },
    analyzedAt: '2026-09-28T01:00:00Z',
    overallStatus: 'CAUTION',
    confirmationRate: '0.00',
    confirmedClaimCount: 0,
    totalClaimCount: 1,
    claims: [
      {
        order: 1,
        claim: '공유된 핵심 주장',
        status: 'INSUFFICIENT',
        reason: '확인 가능한 외부 근거가 부족합니다.',
        evidences: [],
      },
    ],
    expertReviewStatus: 'NOT_REVIEWED',
    limitedEvidence: true,
  }
}
