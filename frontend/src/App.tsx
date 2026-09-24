// 데스크톱 화면 흐름 구성
import { useEffect, useRef, useState } from 'react'
import { Route, Routes, useNavigate } from 'react-router-dom'
import {
  getSession,
  logout,
  type LoginResponse,
  type SessionState,
} from './api/auth'
import { analyzeHealthArticle } from './api/healthAnalysis'
import { analyzeHeadline } from './api/headlineAnalysis'
import { saveHealthRecord } from './api/healthRecords'
import AdminReportsPage from './pages/AdminReportsPage'
import AccountRecoveryPage from './pages/AccountRecoveryPage'
import HealthAnalysisPage from './pages/HealthAnalysisPage'
import HealthResultPage from './pages/HealthResultPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import SavedRecordsPage from './pages/SavedRecordsPage'
import SignupFlowPage from './pages/SignupFlowPage'
import TitleResultPage from './pages/TitleResultPage'
import type {
  HealthAnalysisViewData,
  HealthResultViewData,
} from './types/pageData'
import './styles.css'

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
  const [healthAnalysisData, setHealthAnalysisData] =
    useState<HealthAnalysisViewData>()
  const [healthResultData, setHealthResultData] =
    useState<HealthResultViewData>()
  const [authSession, setAuthSession] = useState<SessionState>({
    authenticated: false,
  })
  const [authError, setAuthError] = useState<string | null>(null)
  const healthAnalysisController = useRef<AbortController | null>(null)
  const authRevision = useRef(0)
  const goTo = (path: string) => {
    void navigate(path)
  }

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
    goTo('/')
  }

  const endSession = async () => {
    authRevision.current += 1
    setAuthError(null)
    try {
      await logout()
      setAuthSession({ authenticated: false })
      goTo('/')
    } catch (error) {
      setAuthError(
        error instanceof Error
          ? error.message
          : '로그아웃하지 못했습니다. 다시 시도해 주세요.',
      )
    }
  }

  const startHeadlineAnalysis = async () => {
    setHeadlineAnalysisPending(true)
    setHeadlineAnalysisError(null)
    try {
      const articleUrl = (await navigator.clipboard.readText()).trim()
      if (!articleUrl) {
        throw new Error('클립보드에 복사된 기사 URL이 없습니다.')
      }
      const result = await analyzeHeadline(articleUrl)
      void navigate('/results/title', { state: { result } })
    } catch (error) {
      setHeadlineAnalysisError(
        error instanceof Error
          ? error.message
          : '기사 제목을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
    } finally {
      setHeadlineAnalysisPending(false)
    }
  }

  const startHealthAnalysis = async () => {
    const controller = new AbortController()
    healthAnalysisController.current?.abort()
    healthAnalysisController.current = controller
    setHealthAnalysisPending(true)
    setHealthAnalysisError(null)
    setHealthResultData(undefined)

    try {
      const articleUrl = (await navigator.clipboard.readText()).trim()
      if (!articleUrl) {
        throw new Error('클립보드에 복사된 기사 URL이 없습니다.')
      }
      void navigate('/analysis/health')
      const result = await analyzeHealthArticle(articleUrl, {
        signal: controller.signal,
        onProgress: setHealthAnalysisData,
      })
      if (
        controller.signal.aborted ||
        healthAnalysisController.current !== controller
      ) {
        return
      }
      setHealthResultData(result)
      void navigate('/results/health')
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
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
    }
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
            authenticated={authSession.authenticated}
            onLogout={() => void endSession()}
            authError={authError}
            isHeadlineAnalysisPending={isHeadlineAnalysisPending}
            headlineAnalysisError={headlineAnalysisError}
            isHealthAnalysisPending={isHealthAnalysisPending}
            healthAnalysisError={healthAnalysisError}
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
            onSave={async (analysisId) => {
              await saveHealthRecord(analysisId)
            }}
          />
        }
      />
      <Route path="/results/title" element={<TitleResultPage />} />
      <Route
        path="/saved"
        element={
          <SavedRecordsPage onReanalyze={() => goTo('/analysis/health')} />
        }
      />
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
      <Route path="/admin/reports" element={<AdminReportsPage />} />
    </Routes>
  )
}

export default App
