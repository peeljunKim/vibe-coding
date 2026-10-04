// 데스크톱 화면 흐름 구성
import { useCallback, useEffect, useRef, useState } from 'react'
import { Route, Routes, useNavigate } from 'react-router-dom'
import {
  getSession,
  logout,
  type LoginResponse,
  type SessionState,
} from './api/auth'
import { ArticleChangedError } from './api/analysisErrors'
import { analyzeHealthArticle } from './api/healthAnalysis'
import { analyzeHeadline } from './api/headlineAnalysis'
import {
  deleteAllHealthRecords,
  deleteHealthRecord,
  replaceHealthRecord,
  saveHealthRecord,
  type HealthRecordSummary,
} from './api/healthRecords'
import { createReport } from './api/reports'
import {
  createHeadlineShare,
  createHealthShare,
  revokeHeadlineShare,
  revokeHealthShare,
} from './api/shares'
import { getDailyUsage } from './api/usage'
import AdminReportsPage from './pages/AdminReportsPage'
import AccountSettingsPage from './pages/AccountSettingsPage'
import AccountRecoveryPage from './pages/AccountRecoveryPage'
import HealthAnalysisPage from './pages/HealthAnalysisPage'
import HealthResultPage from './pages/HealthResultPage'
import HelpPage from './pages/HelpPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import MyReportsPage from './pages/MyReportsPage'
import SavedRecordsPage from './pages/SavedRecordsPage'
import SignupFlowPage from './pages/SignupFlowPage'
import SharedResultPage from './pages/SharedResultPage'
import TitleResultPage from './pages/TitleResultPage'
import type {
  HealthAnalysisViewData,
  HealthResultViewData,
  UsageViewData,
} from './types/pageData'
import './styles.css'

type AnalysisFeature = 'health' | 'headline'

interface PendingReanalysis {
  feature: AnalysisFeature
  articleUrl: string
}

function App() {
  const navigate = useNavigate()
  const [isHeadlineAnalysisPending, setHeadlineAnalysisPending] =
    useState(false)
  const [headlineAnalysisError, setHeadlineAnalysisError] = useState<
    string | null
  >(null)
  const [isHealthAnalysisPending, setHealthAnalysisPending] = useState(false)
  const [healthAnalysisError, setHealthAnalysisError] = useState<string | null>(
    null,
  )
  const [savedRecordsError, setSavedRecordsError] = useState<string | null>(null)
  const [pendingReanalysis, setPendingReanalysis] =
    useState<PendingReanalysis>()
  const [healthAnalysisData, setHealthAnalysisData] =
    useState<HealthAnalysisViewData>()
  const [healthResultData, setHealthResultData] =
    useState<HealthResultViewData>()
  const [healthResultNotice, setHealthResultNotice] = useState<string | null>(
    null,
  )
  const [authSession, setAuthSession] = useState<SessionState>({
    authenticated: false,
  })
  const [authError, setAuthError] = useState<string | null>(null)
  const [usage, setUsage] = useState<UsageViewData>()
  const [usageUnavailable, setUsageUnavailable] = useState(false)
  const healthAnalysisController = useRef<AbortController | null>(null)
  const authRevision = useRef(0)
  const usageRequestRevision = useRef(0)
  const goTo = (path: string) => {
    void navigate(path)
  }

  const refreshDailyUsage = useCallback(async () => {
    const requestedRevision = ++usageRequestRevision.current
    try {
      const current = await getDailyUsage()
      if (requestedRevision !== usageRequestRevision.current) {
        return
      }
      setUsage({
        healthRemaining: current.health.remaining,
        headlineRemaining: current.headline.remaining,
      })
      setUsageUnavailable(false)
    } catch {
      if (requestedRevision !== usageRequestRevision.current) {
        return
      }
      setUsage(undefined)
      setUsageUnavailable(true)
    }
  }, [])

  useEffect(() => {
    const requestTimer = window.setTimeout(() => {
      void refreshDailyUsage()
    }, 0)
    return () => {
      window.clearTimeout(requestTimer)
      usageRequestRevision.current += 1
    }
  }, [refreshDailyUsage])

  useEffect(() => {
    let active = true
    const requestedRevision = authRevision.current
    void getSession()
      .then((session) => {
        if (active && requestedRevision === authRevision.current) {
          setAuthSession(session)
        }
      })
      .catch(() => {
        if (active && requestedRevision === authRevision.current) {
          setAuthSession({ authenticated: false })
        }
      })
    return () => {
      active = false
    }
  }, [])

  const completeLogin = (session: LoginResponse) => {
    authRevision.current += 1
    setAuthError(null)
    setAuthSession(session)
    void refreshDailyUsage()
    goTo('/')
  }

  const endSession = async () => {
    authRevision.current += 1
    setAuthError(null)
    try {
      await logout()
      setAuthSession({ authenticated: false })
      void refreshDailyUsage()
      goTo('/')
    } catch (error) {
      setAuthError(
        error instanceof Error
          ? error.message
          : '로그아웃하지 못했습니다. 다시 시도해 주세요.',
      )
    }
  }

  const runHeadlineAnalysis = async (
    articleUrl: string,
    reanalyze = false,
  ) => {
    setHeadlineAnalysisPending(true)
    setHeadlineAnalysisError(null)
    try {
      const result = await analyzeHeadline(articleUrl, { reanalyze })
      void navigate('/results/title', { state: { result } })
    } catch (error) {
      if (error instanceof ArticleChangedError) {
        setPendingReanalysis({ feature: 'headline', articleUrl })
        setHeadlineAnalysisError(null)
        void navigate('/')
        return
      }
      setHeadlineAnalysisError(
        error instanceof Error
          ? error.message
          : '기사 제목을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
    } finally {
      setHeadlineAnalysisPending(false)
      void refreshDailyUsage()
    }
  }

  const startHeadlineAnalysis = async () => {
    setPendingReanalysis(undefined)
    try {
      const articleUrl = (await navigator.clipboard.readText()).trim()
      if (!articleUrl) {
        throw new Error('클립보드에 복사된 기사 URL이 없습니다.')
      }
      await runHeadlineAnalysis(articleUrl)
    } catch (error) {
      setHeadlineAnalysisError(
        error instanceof Error
          ? error.message
          : '기사 제목을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
    }
  }

  const runHealthAnalysis = async (
    articleUrl: string,
    reanalyze = false,
    replacementRecordId?: string,
  ) => {
    const controller = new AbortController()
    healthAnalysisController.current?.abort()
    healthAnalysisController.current = controller
    setHealthAnalysisPending(true)
    setHealthAnalysisError(null)
    setHealthResultData(undefined)
    setHealthResultNotice(null)

    try {
      void navigate('/analysis/health')
      const result = await analyzeHealthArticle(articleUrl, {
        signal: controller.signal,
        onProgress: setHealthAnalysisData,
        reanalyze,
      })
      if (
        controller.signal.aborted ||
        healthAnalysisController.current !== controller
      ) {
        return
      }
      if (replacementRecordId) {
        try {
          await replaceHealthRecord(replacementRecordId, result.analysisId)
        } catch {
          if (
            controller.signal.aborted ||
            healthAnalysisController.current !== controller
          ) {
            return
          }
          setHealthResultData(result)
          setHealthResultNotice(
            '새 분석 결과를 기존 저장 기록에 반영하지 못했습니다. 새 결과를 별도로 저장할 수 있습니다.',
          )
          void navigate('/results/health')
          return
        }
        if (
          controller.signal.aborted ||
          healthAnalysisController.current !== controller
        ) {
          return
        }
      }
      setHealthResultData(result)
      void navigate('/results/health')
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }
      if (error instanceof ArticleChangedError) {
        setPendingReanalysis({ feature: 'health', articleUrl })
        setHealthAnalysisError(null)
        void navigate('/')
        return
      }
      if (replacementRecordId) {
        setSavedRecordsError(
          error instanceof Error
            ? error.message
            : '다시 분석하지 못했습니다. 기존 기록은 유지됩니다.',
        )
        void navigate('/saved')
        return
      }
      setHealthAnalysisError(
        error instanceof Error
          ? error.message
          : '건강 기사를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
      void navigate('/')
    } finally {
      if (healthAnalysisController.current === controller) {
        healthAnalysisController.current = null
        setHealthAnalysisPending(false)
      }
      void refreshDailyUsage()
    }
  }

  const startHealthAnalysis = async () => {
    setPendingReanalysis(undefined)
    try {
      const articleUrl = (await navigator.clipboard.readText()).trim()
      if (!articleUrl) {
        throw new Error('클립보드에 복사된 기사 URL이 없습니다.')
      }
      await runHealthAnalysis(articleUrl)
    } catch (error) {
      setHealthAnalysisError(
        error instanceof Error
          ? error.message
          : '건강 기사를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
      void navigate('/')
    }
  }

  const confirmReanalysis = () => {
    const request = pendingReanalysis
    if (!request) {
      return
    }

    setPendingReanalysis(undefined)
    if (request.feature === 'health') {
      void runHealthAnalysis(request.articleUrl, true)
      return
    }
    void runHeadlineAnalysis(request.articleUrl, true)
  }

  const cancelHealthAnalysis = () => {
    healthAnalysisController.current?.abort()
    healthAnalysisController.current = null
    setHealthAnalysisPending(false)
    setHealthAnalysisData(undefined)
    goTo('/')
  }

  return (
    <Routes>
      <Route
        path="/"
        element={
          <HomePage
            onStartHealthAnalysis={() => void startHealthAnalysis()}
            onStartHeadlineAnalysis={() => void startHeadlineAnalysis()}
            onOpenSavedRecords={() => goTo('/saved')}
            onOpenReports={() => goTo('/reports')}
            authenticated={authSession.authenticated}
            onLogout={() => void endSession()}
            authError={authError}
            isHeadlineAnalysisPending={isHeadlineAnalysisPending}
            headlineAnalysisError={headlineAnalysisError}
            isHealthAnalysisPending={isHealthAnalysisPending}
            healthAnalysisError={healthAnalysisError}
            reanalysisFeature={pendingReanalysis?.feature}
            onConfirmReanalysis={confirmReanalysis}
            onCancelReanalysis={() => setPendingReanalysis(undefined)}
            {...(usage ? { usage } : {})}
            usageUnavailable={usageUnavailable}
          />
        }
      />
      <Route
        path="/analysis/health"
        element={
          <HealthAnalysisPage
            {...(healthAnalysisData ? { data: healthAnalysisData } : {})}
            onCancel={cancelHealthAnalysis}
          />
        }
      />
      <Route
        path="/results/health"
        element={
          <HealthResultPage
            {...(healthResultData ? { data: healthResultData } : {})}
            onNewArticle={() => goTo('/')}
            onSave={saveHealthRecord}
            onShare={createHealthShare}
            onRevokeShare={revokeHealthShare}
            authenticated={authSession.authenticated}
            notice={healthResultNotice}
            onReport={async (analysisId, reportType, description) => {
              await createReport({
                analysisType: 'HEALTH',
                analysisId,
                reportType,
                description,
              })
            }}
          />
        }
      />
      <Route
        path="/results/title"
        element={
          <TitleResultPage
            authenticated={authSession.authenticated}
            onShare={createHeadlineShare}
            onRevokeShare={revokeHeadlineShare}
            onReport={async (analysisId, reportType, description) => {
              await createReport({
                analysisType: 'HEADLINE',
                analysisId,
                reportType,
                description,
              })
            }}
          />
        }
      />
      <Route
        path="/saved"
        element={
          <SavedRecordsPage
            actionError={savedRecordsError}
            onReanalyze={async (record: HealthRecordSummary) => {
              setSavedRecordsError(null)
              await runHealthAnalysis(record.articleUrl, true, record.id)
            }}
            onDelete={async (recordId) => {
              setSavedRecordsError(null)
              await deleteHealthRecord(recordId)
            }}
            onDeleteAll={async () => {
              setSavedRecordsError(null)
              await deleteAllHealthRecords()
            }}
            onOpenReports={() => goTo('/reports')}
            onOpenAccountSettings={() => goTo('/settings/account')}
          />
        }
      />
      <Route
        path="/settings/account"
        element={
          <AccountSettingsPage
            onHome={() => goTo('/')}
            onSavedRecords={() => goTo('/saved')}
            onOpenReports={() => goTo('/reports')}
            onLogin={() => goTo('/login')}
            onWithdrawalRequested={() => {
              authRevision.current += 1
              setAuthSession({ authenticated: false })
            }}
          />
        }
      />
      <Route path="/reports" element={<MyReportsPage />} />
      <Route
        path="/login"
        element={
          <LoginPage
            onHome={() => goTo('/')}
            onStartSignup={() => goTo('/signup')}
            onStartRecovery={() => goTo('/account-recovery')}
            onAuthenticated={completeLogin}
          />
        }
      />
      <Route
        path="/signup"
        element={<SignupFlowPage onLogin={() => goTo('/login')} />}
      />
      <Route
        path="/account-recovery"
        element={
          <AccountRecoveryPage
            onHome={() => goTo('/')}
            onLogin={() => goTo('/login')}
          />
        }
      />
      <Route path="/help" element={<HelpPage />} />
      <Route path="/admin/reports" element={<AdminReportsPage />} />
      <Route
        path="/share/health"
        element={<SharedResultPage type="health" />}
      />
      <Route
        path="/share/headline"
        element={<SharedResultPage type="headline" />}
      />
    </Routes>
  )
}

export default App
