// 건강 분석 사용자 흐름 Browser 회귀 검증
import { expect, test, type Page } from '@playwright/test'

const ARTICLE_URL = 'https://news.example.com/health-article'
const browserErrors = new WeakMap<Page, string[]>()

const mockCsrf = async (page: Page) => {
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
}

const writeArticleUrl = async (page: Page) => {
  await page.goto('/')
  await page.evaluate(
    (articleUrl) => navigator.clipboard.writeText(articleUrl),
    ARTICLE_URL,
  )
}

test.beforeEach(async ({ page }) => {
  const errors: string[] = []
  browserErrors.set(page, errors)
  page.on('console', (message) => {
    if (message.type() === 'error') {
      errors.push(message.text())
    }
  })
  page.on('pageerror', (error) => errors.push(error.message))
  await mockCsrf(page)
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

test('완료 결과는 표시하고 새로고침 뒤에는 비영속 안내를 표시한다', async ({
  page,
}) => {
  await page.route('**/api/analyses/health', (route) =>
    route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        analysisId: 'health-e2e-completed',
        deadlineAt: '2099-09-20T00:01:30Z',
        pollAfterSeconds: 0,
        guestAccessToken: 'test-guest-health-token',
      }),
    }),
  )
  let pollCount = 0
  await page.route('**/api/analyses/health/health-e2e-completed', (route) => {
    pollCount += 1
    if (pollCount === 1) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          analysisId: 'health-e2e-completed',
          status: 'PROCESSING',
          stage: 'SEARCHING_EVIDENCE',
          pollAfterSeconds: 0,
        }),
      })
    }

    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        analysisId: 'health-e2e-completed',
        status: 'COMPLETED',
        stage: 'COMPLETED',
        result: {
          article: {
            url: ARTICLE_URL,
            title: '검증된 건강 기사 제목',
            publisher: '테스트 언론사',
          },
          analyzedAt: '2026-09-20T00:00:03Z',
          claims: [
            {
              order: 1,
              claim: '브라우저에서 확인한 건강 기사 주장',
              status: 'INSUFFICIENT',
              reason: '확인 가능한 외부 근거가 부족합니다.',
              evidences: [],
            },
          ],
        },
      }),
    })
  })
  await writeArticleUrl(page)

  await page.getByRole('button', { name: '복사한 건강 기사 확인하기' }).click()

  await expect(
    page.getByRole('heading', {
      name: '브라우저에서 확인한 건강 기사 주장',
    }),
  ).toBeVisible()
  await page.reload()
  await expect(
    page.getByRole('heading', { name: '[핵심 주장 데이터가 필요합니다.]' }),
  ).toBeVisible()
})

test('로그인 회원은 완료된 건강 분석 결과를 명시적으로 저장한다', async ({
  page,
}) => {
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
  await page.route('**/api/analyses/health', (route) =>
    route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        analysisId: 'health-e2e-saved',
        deadlineAt: '2099-09-20T00:01:30Z',
        pollAfterSeconds: 0,
      }),
    }),
  )
  await page.route('**/api/analyses/health/health-e2e-saved', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        analysisId: 'health-e2e-saved',
        status: 'COMPLETED',
        stage: 'COMPLETED',
        result: {
          article: {
            url: ARTICLE_URL,
            title: '저장할 건강 기사',
            publisher: '테스트 언론사',
          },
          analyzedAt: '2026-09-20T00:00:03Z',
          claims: [
            {
              order: 1,
              claim: '저장할 건강 기사 주장',
              status: 'INSUFFICIENT',
              reason: '확인 가능한 외부 근거가 부족합니다.',
              evidences: [],
            },
          ],
        },
      }),
    }),
  )
  let requestedAnalysisId: string | undefined
  await page.route('**/api/health-records', async (route) => {
    const body = route.request().postDataJSON() as { analysisId?: string }
    requestedAnalysisId = body.analysisId
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 31,
        title: '저장할 건강 기사',
        overallStatus: 'CAUTION',
        analyzedAt: '2026-09-20T00:00:03Z',
        expiresAt: '2026-10-20T00:00:03Z',
      }),
    })
  })
  await writeArticleUrl(page)

  await page.getByRole('button', { name: '복사한 건강 기사 확인하기' }).click()
  await page.getByRole('button', { name: '결과 저장' }).click()

  await expect(page.getByRole('status')).toHaveText('결과가 저장되었습니다.')
  expect(requestedAnalysisId).toBe('health-e2e-saved')
})

test('로그인 회원의 만료되지 않은 저장 기록을 목록에 표시한다', async ({
  page,
}) => {
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
  await page.route('**/api/health-records?page=0&size=20', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: 31,
            title: '목록에 저장된 건강 기사',
            overallStatus: 'CAUTION',
            analyzedAt: '2026-09-20T00:00:03Z',
            expiresAt: '2026-10-20T00:00:03Z',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 21,
        totalPages: 2,
        hasNext: true,
      }),
    }),
  )
  await page.route('**/api/health-records?page=1&size=20', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: 11,
            title: '다음 페이지에 저장된 건강 기사',
            overallStatus: 'RELIABLE',
            analyzedAt: '2026-09-19T00:00:03Z',
            expiresAt: '2026-10-19T00:00:03Z',
          },
        ],
        page: 1,
        size: 20,
        totalElements: 21,
        totalPages: 2,
        hasNext: false,
      }),
    }),
  )

  await page.goto('/saved')

  await expect(
    page.getByRole('heading', { name: '목록에 저장된 건강 기사' }),
  ).toBeVisible()
  await expect(page.getByText('남은 기록 21개')).toBeVisible()
  await page.getByRole('button', { name: '다음 페이지' }).click()
  await expect(
    page.getByRole('heading', { name: '다음 페이지에 저장된 건강 기사' }),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: '이전 페이지' })).toBeEnabled()
  await expect(page.getByRole('button', { name: '다음 페이지' })).toBeDisabled()
})

test('건강 분야가 아닌 기사는 내부 상세 없이 중단 안내를 표시한다', async ({
  page,
}) => {
  await page.route('**/api/analyses/health', (route) =>
    route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        analysisId: 'health-e2e-rejected',
        deadlineAt: '2099-09-20T00:01:30Z',
        pollAfterSeconds: 0,
        guestAccessToken: 'test-guest-health-token',
      }),
    }),
  )
  await page.route('**/api/analyses/health/health-e2e-rejected', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        analysisId: 'health-e2e-rejected',
        status: 'FAILED',
        stage: 'FAILED',
        error: {
          code: 'ARTICLE_NOT_HEALTH_RELATED',
          detail: '내부 판별 상세 정보',
        },
      }),
    }),
  )
  await writeArticleUrl(page)

  await page.getByRole('button', { name: '복사한 건강 기사 확인하기' }).click()

  await expect(page.getByRole('alert')).toHaveText(
    '건강·의학·보건 관련 기사로 확인되지 않았습니다.',
  )
  await expect(page.getByText('내부 판별 상세 정보')).toHaveCount(0)
})

test('분석 접수 불가 상태에는 안전한 재시도 안내를 표시한다', async ({
  page,
}) => {
  await page.route('**/api/analyses/health', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        code: 'ANALYSIS_SERVICE_UNAVAILABLE',
        detail: 'Redis 연결 내부 정보',
      }),
    }),
  )
  await writeArticleUrl(page)

  await page.getByRole('button', { name: '복사한 건강 기사 확인하기' }).click()

  await expect(page.getByRole('alert')).toHaveText(
    '분석 서비스를 현재 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.',
  )
  await expect(page.getByText('Redis 연결 내부 정보')).toHaveCount(0)
  expect(browserErrors.get(page)).toEqual([
    'Failed to load resource: the server responded with a status of 503 (Service Unavailable)',
  ])
  browserErrors.set(page, [])
})

test('분석 취소 뒤 늦게 도착한 완료 결과를 폐기한다', async ({ page }) => {
  await page.route('**/api/analyses/health', (route) =>
    route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        analysisId: 'health-e2e-cancelled',
        deadlineAt: '2099-09-20T00:01:30Z',
        pollAfterSeconds: 0,
        guestAccessToken: 'test-guest-health-token',
      }),
    }),
  )
  let notifyPollStarted: (() => void) | undefined
  const pollStarted = new Promise<void>((resolve) => {
    notifyPollStarted = resolve
  })
  await page.route(
    '**/api/analyses/health/health-e2e-cancelled',
    async (route) => {
      notifyPollStarted?.()
      await new Promise((resolve) => setTimeout(resolve, 300))
      await route
        .fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            analysisId: 'health-e2e-cancelled',
            status: 'COMPLETED',
            stage: 'COMPLETED',
            result: {
              article: {
                url: ARTICLE_URL,
                title: '취소 뒤 도착한 기사',
                publisher: '테스트 언론사',
              },
              analyzedAt: '2026-09-20T00:00:03Z',
              claims: [
                {
                  order: 1,
                  claim: '폐기되어야 할 건강 기사 주장',
                  status: 'INSUFFICIENT',
                  reason: '확인 가능한 외부 근거가 부족합니다.',
                  evidences: [],
                },
              ],
            },
          }),
        })
        .catch(() => undefined)
    },
  )
  await writeArticleUrl(page)

  await page.getByRole('button', { name: '복사한 건강 기사 확인하기' }).click()
  await pollStarted
  await page.getByRole('button', { name: '분석 취소' }).click()

  await expect(
    page.getByRole('heading', {
      name: '기사의 주장과 제목을 쉽게 확인해 보세요',
    }),
  ).toBeVisible()
  await page.waitForTimeout(400)
  await expect(
    page.getByRole('heading', { name: '폐기되어야 할 건강 기사 주장' }),
  ).toHaveCount(0)
})
