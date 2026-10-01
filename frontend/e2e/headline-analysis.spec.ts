// 제목 분석 재분석 Browser 회귀 검증
import { expect, test, type Page } from '@playwright/test'

const ARTICLE_URL = 'https://news.example.com/changed-headline'
const browserErrors = new WeakMap<Page, string[]>()

test.beforeEach(async ({ page }) => {
  const errors: string[] = []
  browserErrors.set(page, errors)
  page.on('console', (message) => {
    if (message.type() === 'error') {
      errors.push(message.text())
    }
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
        'set-cookie': 'XSRF-TOKEN=test-csrf-token; Path=/; SameSite=Lax',
      },
      contentType: 'application/json',
      body: '{}',
    }),
  )
})

test.afterEach(({ page }) => {
  expect(browserErrors.get(page)).toEqual([])
})

test('변경된 기사 제목은 사용자 확인 뒤 최신 내용으로 재분석한다', async ({
  page,
}) => {
  const requestBodies: unknown[] = []
  let acceptanceCount = 0
  await page.route('**/api/analyses/headline', async (route) => {
    requestBodies.push(route.request().postDataJSON())
    acceptanceCount += 1
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        analysisId:
          acceptanceCount === 1
            ? 'headline-e2e-changed'
            : 'headline-e2e-reanalyzed',
        deadlineAt: '2099-09-20T00:01:30Z',
        pollAfterSeconds: 0,
        guestAccessToken: 'test-guest-headline-token',
      }),
    })
  })
  await page.route('**/api/analyses/headline/headline-e2e-changed', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'FAILED',
        error: { code: 'ARTICLE_CHANGED' },
      }),
    }),
  )
  await page.route(
    '**/api/analyses/headline/headline-e2e-reanalyzed',
    (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'COMPLETED',
          result: {
            article: {
              url: ARTICLE_URL,
              title: '최신 내용으로 다시 읽은 기사 제목',
              publisher: '테스트 언론사',
            },
            analyzedAt: '2026-09-20T00:00:03Z',
            issues: [
              {
                type: 'NO_ISSUE',
                explanation: '최신 제목과 본문의 핵심 내용이 일치합니다.',
              },
            ],
          },
        }),
      }),
  )
  await page.goto('/')
  await page.evaluate(
    (articleUrl) => navigator.clipboard.writeText(articleUrl),
    ARTICLE_URL,
  )

  await page.getByRole('button', { name: '복사한 기사 제목 확인하기' }).click()
  await expect(page.getByRole('button', { name: '최신 내용 재분석' })).toBeVisible()
  await page.getByRole('button', { name: '최신 내용 재분석' }).click()

  await expect(
    page.getByRole('heading', { name: '최신 내용으로 다시 읽은 기사 제목' }),
  ).toBeVisible()
  expect(requestBodies).toEqual([
    { articleUrl: ARTICLE_URL },
    { articleUrl: ARTICLE_URL, reanalyze: true },
  ])
})
