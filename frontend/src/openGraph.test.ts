// 공유 링크 공통 Open Graph 메타데이터 검증
import { describe, expect, it } from 'vitest'
import indexHtml from '../index.html?raw'

describe('공유 링크 Open Graph 메타데이터', () => {
  it('검색 수집을 차단하면서 공통 공유 미리보기를 제공한다', () => {
    expect(indexHtml).toContain(
      '<meta name="robots" content="noindex, nofollow" />',
    )
    expect(indexHtml).toContain('<meta property="og:locale" content="ko_KR" />')
    expect(indexHtml).toContain('<meta property="og:type" content="website" />')
    expect(indexHtml).toContain(
      '<meta property="og:site_name" content="기사체크" />',
    )
    expect(indexHtml).toContain(
      '<meta property="og:title" content="기사체크 분석 결과" />',
    )
    expect(indexHtml).toContain('property="og:description"')
    expect(indexHtml).toContain(
      '<meta property="og:image" content="/og-share.png" />',
    )
    expect(indexHtml).toContain(
      '<meta property="og:image:width" content="1200" />',
    )
    expect(indexHtml).toContain(
      '<meta property="og:image:height" content="630" />',
    )
    expect(indexHtml).toContain(
      '<meta name="twitter:card" content="summary_large_image" />',
    )
    expect(indexHtml).toContain(
      '<meta name="twitter:title" content="기사체크 분석 결과" />',
    )
    expect(indexHtml).toContain('name="twitter:description"')
    expect(indexHtml).toContain(
      '<meta name="twitter:image" content="/og-share.png" />',
    )
  })
})
