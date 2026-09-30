// 회원 계정 설정과 탈퇴 신청 화면
import { useState } from 'react'
import {
  requestAccountWithdrawal,
  type AccountWithdrawalSchedule,
} from '../api/accountWithdrawal'
import AppHeader from '../components/AppHeader'

interface AccountSettingsPageProps {
  onHome: () => void
  onSavedRecords: () => void
  onOpenReports: () => void
  onLogin: () => void
  onWithdrawalRequested: () => void
}

const formatDate = (value: string) =>
  new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value))

function AccountSettingsPage({
  onHome,
  onSavedRecords,
  onOpenReports,
  onLogin,
  onWithdrawalRequested,
}: AccountSettingsPageProps) {
  const [confirming, setConfirming] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [schedule, setSchedule] = useState<AccountWithdrawalSchedule>()

  const submitWithdrawal = async () => {
    setSubmitting(true)
    setError(null)
    try {
      const result = await requestAccountWithdrawal()
      setSchedule(result)
      setConfirming(false)
      onWithdrawalRequested()
    } catch (submitError) {
      setError(
        submitError instanceof Error
          ? submitError.message
          : '탈퇴를 신청하지 못했습니다. 다시 시도해 주세요.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="app-page account-settings-page">
      <AppHeader section="계정 설정" onHome={onHome}>
        <button type="button" onClick={onOpenReports}>
          내 신고 내역
        </button>
      </AppHeader>

      <main className="saved-layout">
        <aside className="saved-sidebar">
          <h2>마이페이지</h2>
          <nav aria-label="마이페이지 메뉴">
            <button type="button" onClick={onSavedRecords}>
              저장한 건강 뉴스
            </button>
            <button type="button" onClick={onOpenReports}>
              내 신고 내역
            </button>
            <span>연결된 소셜 계정</span>
            <span className="saved-sidebar__active">계정 설정</span>
          </nav>
        </aside>

        <section className="account-settings-content" aria-labelledby="account-settings-heading">
          {schedule ? (
            <div className="account-settings-complete">
              <span aria-hidden="true">✓</span>
              <h1 id="account-settings-heading">탈퇴 신청이 완료되었습니다</h1>
              <p>
                {formatDate(schedule.recoveryDeadline)}까지 로그인하면 계정을
                복구할 수 있습니다.
              </p>
              <p>
                남아 있는 계정과 기록은 {formatDate(schedule.scheduledDeletionAt)}에
                영구 삭제됩니다.
              </p>
              <button className="primary-button" type="button" onClick={onLogin}>
                로그인 화면으로 이동
              </button>
            </div>
          ) : (
            <>
              <header className="account-settings-heading">
                <h1 id="account-settings-heading">계정 설정</h1>
                <p>계정 이용과 탈퇴 일정을 확인할 수 있습니다.</p>
              </header>
              <section className="account-withdrawal-card">
                <span className="status-badge status-badge--warning">주의 필요</span>
                <h2>회원 탈퇴</h2>
                <div className="account-withdrawal-timeline" aria-label="탈퇴 처리 일정">
                  <div>
                    <strong>신청 즉시</strong>
                    <span>로그아웃 및 계정 비활성화</span>
                  </div>
                  <div>
                    <strong>7일 이내</strong>
                    <span>로그인 후 계정 복구 가능</span>
                  </div>
                  <div>
                    <strong>탈퇴 신청 후 30일</strong>
                    <span>남은 계정과 기록 영구 삭제</span>
                  </div>
                </div>
                <p className="account-withdrawal-card__notice">
                  분석 기록처럼 자체 보관 기한이 더 짧은 데이터는 먼저 삭제될 수 있습니다.
                </p>
                {error ? <p role="alert">{error}</p> : null}
                <button
                  className="account-withdrawal-card__button"
                  type="button"
                  onClick={() => setConfirming(true)}
                >
                  회원 탈퇴 신청
                </button>
              </section>
            </>
          )}
        </section>
      </main>

      {confirming ? (
        <div className="report-dialog-backdrop" role="presentation">
          <section
            className="report-dialog withdrawal-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="withdrawal-confirm-heading"
          >
            <h2 id="withdrawal-confirm-heading">회원 탈퇴를 신청할까요?</h2>
            <p>
              신청 즉시 로그아웃됩니다. 7일 안에는 로그인하여 복구할 수 있고,
              이후에는 복구할 수 없습니다.
            </p>
            <div className="report-dialog__actions">
              <button type="button" onClick={() => setConfirming(false)}>
                취소
              </button>
              <button
                className="withdrawal-confirm-button"
                type="button"
                disabled={submitting}
                onClick={() => void submitWithdrawal()}
              >
                {submitting ? '신청 중' : '탈퇴 신청 확정'}
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </div>
  )
}

export default AccountSettingsPage
