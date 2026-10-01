// 분석 사용자 확인 오류
export class ArticleChangedError extends Error {
  readonly articleUrl: string

  constructor(articleUrl: string) {
    super('기사 내용이 분석 당시와 달라졌습니다. 최신 내용으로 다시 분석하시겠습니까?')
    this.name = 'ArticleChangedError'
    this.articleUrl = articleUrl
  }
}
