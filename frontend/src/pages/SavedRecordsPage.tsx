// 저장된 건강 뉴스 목록 화면
import AppHeader from '../components/AppHeader'
import type { SavedRecordViewData } from '../types/pageData'

interface SavedRecordsPageProps {
  records?: SavedRecordViewData[]
  onReanalyze?: (recordId: string) => void
  onDelete?: (recordId: string) => void
  onDeleteAll?: () => void
}

const statusLabels: Record<SavedRecordViewData['overallStatus'], string> = {
  RELIABLE: '신뢰 가능',
  CAUTION: '주의 필요',
  DOUBTFUL: '의심',
}

function SavedRecordsPage({
  records,
  onReanalyze,
  onDelete,
  onDeleteAll,
}: SavedRecordsPageProps) {
  return (
    <div className="app-page saved-page">
      <AppHeader section="저장 기록">
        <span>내 신고 내역</span>
        <span>설정</span>
      </AppHeader>

      <main className="saved-layout">
        <aside className="saved-sidebar">
          <h2>마이페이지</h2>
          <nav aria-label="마이페이지 메뉴">
            <span className="saved-sidebar__active">저장한 건강 뉴스</span>
            <span>내 신고 내역</span>
            <span>연결된 소셜 계정</span>
            <span>계정 설정</span>
          </nav>
        </aside>

        <section className="saved-content" aria-labelledby="saved-heading">
          <div className="saved-content__heading">
            <div>
              <h1 id="saved-heading">저장한 건강 뉴스</h1>
              <p>사용자가 저장한 결과만 분석일로부터 30일 보관됩니다.</p>
            </div>
            <span>
              {records ? `남은 기록 ${records.length}개` : '기록 확인 전'}
            </span>
          </div>

          <div className="saved-records">
            {records?.length ? (
              records.map((record) => (
                <article className="saved-record" key={record.id}>
                  <div className="saved-record__content">
                    <span
                      className={`status-badge status-badge--${record.overallStatus === 'RELIABLE' ? 'success' : record.overallStatus === 'DOUBTFUL' ? 'danger' : 'warning'}`}
                    >
                      {statusLabels[record.overallStatus]}
                    </span>
                    <h2>{record.title}</h2>
                    <p>
                      분석일 {record.analyzedAt} · {record.expiresAt} 삭제
                    </p>
                  </div>
                  <div className="saved-record__actions">
                    <button
                      type="button"
                      disabled={!onReanalyze}
                      onClick={() => onReanalyze?.(record.id)}
                    >
                      다시 분석
                    </button>
                    <button
                      type="button"
                      disabled={!onDelete}
                      onClick={() => onDelete?.(record.id)}
                    >
                      삭제
                    </button>
                  </div>
                </article>
              ))
            ) : (
              <div className="saved-records__empty" role="status">
                {records
                  ? '저장한 건강 뉴스가 없습니다.'
                  : '[저장 기록 목록 데이터가 필요합니다.]'}
              </div>
            )}
          </div>

          <aside className="saved-notice" role="note">
            <h2>다시 분석 안내</h2>
            <p>다시 분석은 건강 뉴스 일일 횟수 1회를 사용합니다.</p>
            <p>성공하면 기존 결과와 공유 링크가 새 결과로 교체됩니다.</p>
          </aside>

          <button
            className="text-action text-action--danger saved-delete-all"
            type="button"
            disabled={!records?.length || !onDeleteAll}
            onClick={onDeleteAll}
          >
            전체 기록 삭제
          </button>
        </section>
      </main>
    </div>
  )
}

export default SavedRecordsPage
