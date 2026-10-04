// 문제 신고 사용자와 관리자 Browser 흐름 검증
import { expect, test, type Page } from '@playwright/test'
import { mockDailyUsage } from './support/daily-usage'

const ARTICLE_URL = 'https://news.example.com/report-article'
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
        'set-cookie': 'XSRF-TOKEN=report-e2e-csrf; Path=/; SameSite=Lax',
      },
      contentType: 'application/json',
      body: '{}',
    }),
  )
})

test.afterEach(({ page }) => {
  expect(browserErrors.get(page)).toEqual([])
})

test('로그인 회원이 건강 분석 결과를 신고하고 내 신고 내역을 확인한다', async ({
  page,
}) => {
  await page.route('**/api/analyses/health', (route) =>
    route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        analysisId: 'health-report-e2e',
        deadlineAt: '2099-09-20T00:01:30Z',
        pollAfterSeconds: 0,
      }),
    }),
  )
  await page.route('**/api/analyses/health/health-report-e2e', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        analysisId: 'health-report-e2e',
        status: 'COMPLETED',
        stage: 'COMPLETED',
        result: {
          article: {
            url: ARTICLE_URL,
            title: '신고할 건강 기사',
            publisher: '테스트 언론사',
          },
          analyzedAt: '2026-09-27T00:00:03Z',
          claims: [
            {
              order: 1,
              claim: '신고할 건강 기사 주장',
              status: 'INSUFFICIENT',
              reason: '확인 가능한 외부 근거가 부족합니다.',
              evidences: [],
            },
          ],
        },
      }),
    }),
  )
  let createBody: Record<string, unknown> | undefined
  await page.route('**/api/reports', async (route) => {
    if (route.request().method() === 'POST') {
      createBody = route.request().postDataJSON() as Record<string, unknown>
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 17,
          status: 'OPEN',
          createdAt: '2026-09-27T01:00:00Z',
        }),
      })
    }
    return route.fallback()
  })
  await page.route('**/api/reports?page=0&size=20', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: 17,
            analysisType: 'HEALTH',
            reportType: 'WRONG_JUDGMENT',
            articleTitle: '신고할 건강 기사',
            publisherName: '테스트 언론사',
            status: 'OPEN',
            createdAt: '2026-09-27T01:00:00Z',
            updatedAt: '2026-09-27T01:00:00Z',
            completedAt: null,
            adminReply: null,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      }),
    }),
  )

  await page.goto('/')
  await page.evaluate((url) => navigator.clipboard.writeText(url), ARTICLE_URL)
  await page.getByRole('button', { name: '복사한 건강 기사 확인하기' }).click()
  await expect(
    page.getByRole('heading', { name: '신고할 건강 기사 주장' }),
  ).toBeVisible()
  await page.getByRole('button', { name: '문제가 있다면 결과 신고' }).click()
  await page.getByLabel('간단한 설명').fill('판정을 다시 확인해 주세요.')
  await page.getByRole('button', { name: '신고 접수' }).click()

  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(createBody).toEqual({
    analysisType: 'HEALTH',
    analysisId: 'health-report-e2e',
    reportType: 'WRONG_JUDGMENT',
    description: '판정을 다시 확인해 주세요.',
  })

  await page.goto('/reports')
  await expect(
    page.getByRole('heading', { name: '신고할 건강 기사' }),
  ).toBeVisible()
  await expect(page.getByText('확인 전')).toBeVisible()
  await expect(page.getByText('아직 등록된 답변이 없습니다.')).toBeVisible()
})

test('ADMIN은 신고 상세를 확인하고 처리 완료 답변을 저장한다', async ({
  page,
}) => {
  const resultSnapshot = {
    schemaVersion: 1,
    analysisType: 'HEALTH',
    result: {
      claims: [
        {
          claim: '신고된 핵심 주장',
          reason: '근거를 다시 확인해야 합니다.',
          evidences: [
            {
              title: '공식 근거',
              sourceUrl: 'https://evidence.example.com/guide',
              summary: '신고 당시 저장된 근거 요약',
            },
          ],
        },
      ],
    },
  }
  const summary = {
    id: 17,
    analysisType: 'HEALTH',
    reportType: 'WRONG_JUDGMENT',
    articleTitle: '관리할 건강 기사',
    publisherName: '테스트 언론사',
    status: 'OPEN',
    createdAt: '2026-09-27T01:00:00Z',
    updatedAt: '2026-09-27T01:00:00Z',
    completedAt: null,
    adminReply: null,
  }
  await page.route('**/api/admin/reports?page=0&size=20', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [summary],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      }),
    }),
  )
  await page.route('**/api/admin/reports/17', async (route) => {
    if (route.request().method() === 'PATCH') {
      expect(route.request().postDataJSON()).toEqual({
        status: 'RESOLVED',
        adminReply: '신고 내용을 확인했습니다.',
        version: 0,
      })
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...summary,
          status: 'RESOLVED',
          updatedAt: '2026-09-27T03:00:00Z',
          completedAt: '2026-09-27T03:00:00Z',
          adminReply: '신고 내용을 확인했습니다.',
          description: '판정을 다시 확인해 주세요.',
          articleUrl: ARTICLE_URL,
          resultSnapshot,
          version: 1,
        }),
      })
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        ...summary,
        description: '판정을 다시 확인해 주세요.',
        articleUrl: ARTICLE_URL,
        resultSnapshot,
        version: 0,
      }),
    })
  })

  await page.goto('/admin/reports')
  await expect(page.getByText('판정을 다시 확인해 주세요.')).toBeVisible()
  await expect(page.getByText('신고된 핵심 주장')).toBeVisible()
  await expect(
    page.getByRole('link', { name: '공식 근거 원문 보기' }),
  ).toHaveAttribute('href', 'https://evidence.example.com/guide')
  await page.getByLabel('처리 상태').selectOption({ label: '처리 완료' })
  await page.getByLabel('관리자 답변').fill('신고 내용을 확인했습니다.')
  await page.getByRole('button', { name: '상태와 답변 저장' }).click()

  await expect(page.locator('.report-detail .status-badge')).toHaveText(
    '처리 완료',
  )
  await expect(page.getByLabel('관리자 답변')).toHaveValue(
    '신고 내용을 확인했습니다.',
  )
})
