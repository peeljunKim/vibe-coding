// Backend Origin 환경별 경계 검증
import { describe, expect, it } from 'vitest'
import { resolveBackendBaseUrl } from './backend'

describe('resolveBackendBaseUrl', () => {
  it('운영 환경은 Vercel 동일 출처 상대 경로를 사용한다', () => {
    expect(resolveBackendBaseUrl(undefined)).toBe('')
  })

  it('Local 개발은 기존 Backend 주소를 유지한다', () => {
    expect(resolveBackendBaseUrl(undefined, 'http://localhost:8080')).toBe(
      'http://localhost:8080',
    )
  })

  it('명시한 Backend 주소의 마지막 슬래시를 제거한다', () => {
    expect(resolveBackendBaseUrl('https://api.example.com/')).toBe(
      'https://api.example.com',
    )
  })
})
