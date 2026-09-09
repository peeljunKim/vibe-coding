// 데스크톱 화면 흐름 구성
import { Route, Routes, useNavigate } from 'react-router-dom'
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
  const goTo = (path: string) => {
    void navigate(path)
  }

  return (
    <Routes>
      <Route
        path="/"
        element={
          <HomePage
            onStartHealthAnalysis={() => goTo('/analysis/health')}
            onOpenSavedRecords={() => goTo('/saved')}
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
