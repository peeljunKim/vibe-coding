// 데스크톱 화면 흐름 구성
import { useState } from 'react'
import { Route, Routes, useNavigate } from 'react-router-dom'
import { analyzeHeadline } from './api/headlineAnalysis'
import AdminReportsPage from './pages/AdminReportsPage'
import HealthAnalysisPage from './pages/HealthAnalysisPage'
import HealthResultPage from './pages/HealthResultPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import SavedRecordsPage from './pages/SavedRecordsPage'
import SignupFlowPage from './pages/SignupFlowPage'
import TitleResultPage from './pages/TitleResultPage'
import './styles.css'

function App() {
  const navigate = useNavigate()
  const [isHeadlineAnalysisPending, setHeadlineAnalysisPending] =
    useState(false)
  const [headlineAnalysisError, setHeadlineAnalysisError] = useState<
    string | null
  >(null)
  const goTo = (path: string) => {
    void navigate(path)
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

  return (
    <Routes>
      <Route
        path="/"
        element={
          <HomePage
            onStartHealthAnalysis={() => goTo('/analysis/health')}
            onStartHeadlineAnalysis={() => void startHeadlineAnalysis()}
            onOpenSavedRecords={() => goTo('/saved')}
            isHeadlineAnalysisPending={isHeadlineAnalysisPending}
            headlineAnalysisError={headlineAnalysisError}
          />
        }
      />
      <Route
        path="/analysis/health"
        element={<HealthAnalysisPage onCancel={() => goTo('/')} />}
      />
      <Route
        path="/results/health"
        element={<HealthResultPage onNewArticle={() => goTo('/')} />}
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
          />
        }
      />
      <Route
        path="/signup"
        element={<SignupFlowPage onLogin={() => goTo('/login')} />}
      />
      <Route path="/admin/reports" element={<AdminReportsPage />} />
    </Routes>
  )
}

export default App
