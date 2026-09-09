// 화면별 동적 데이터 입력 모델
export interface UsageViewData {
  healthRemaining: number
  headlineRemaining: number
}

export interface ArticleViewData {
  title: string
  publisher: string
  url: string
  publishedAt?: string
  category?: string
}

export type AnalysisStage =
  'ARTICLE_CHECKED' | 'EVIDENCE_SEARCHING' | 'RESULT_PREPARING'

export interface HealthAnalysisViewData {
  article: ArticleViewData
  stage: AnalysisStage
}

export type HealthClaimStatus =
  'SUPPORTED' | 'NEEDS_REVIEW' | 'CONTRADICTED' | 'INSUFFICIENT'

export interface EvidenceViewData {
  id: string
  title: string
  provider: string
  sourceUrl: string
  summary: string
  publishedOrUpdatedDate?: string
  sourceType?: string
}

export interface HealthResultViewData {
  article: ArticleViewData
  analyzedAt: string
  claim: string
  claimStatus: HealthClaimStatus
  reasons: string[]
  evidences: EvidenceViewData[]
}

export type HeadlineIssueType =
  'NO_ISSUE' | 'EXAGGERATED' | 'OMITS_CONTEXT' | 'MISMATCH'

export interface HeadlineIssueViewData {
  type: HeadlineIssueType
  explanation: string
}

export interface TitleResultViewData {
  article: ArticleViewData
  analyzedAt: string
  issues: HeadlineIssueViewData[]
  alternativeHeadline?: string
}

export interface SavedRecordViewData {
  id: string
  title: string
  overallStatus: 'RELIABLE' | 'CAUTION' | 'DOUBTFUL'
  analyzedAt: string
  expiresAt: string
}

export interface SignupVerificationViewData {
  remainingAttempts: number
  resendAvailableInSeconds: number
}

export type ReportStatus = '확인 전' | '확인 중' | '처리 완료'

export interface ReportSummaryViewData {
  id: string
  title: string
  status: ReportStatus
  reportedAt: string
  publisher: string
  analysisType: string
  description: string
  answer?: string
}

export interface ReportStatsViewData {
  waiting: number
  working: number
  complete: number
}
