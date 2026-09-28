// 분석 결과 공유 대화상자
import { useEffect, useRef, useState } from 'react'
import { buildShareUrl, type CreatedShare } from '../api/shares'

interface ShareDialogProps {
  requiresSave?: boolean
  onClose: () => void
  onCreate: () => Promise<CreatedShare>
  onRevoke: (token: string) => Promise<void>
}

function ShareDialog({
  requiresSave = false,
  onClose,
  onCreate,
  onRevoke,
}: ShareDialogProps) {
  const actionRef = useRef<HTMLButtonElement>(null)
  const shareUrlRef = useRef<HTMLInputElement>(null)
  const [created, setCreated] = useState<CreatedShare | null>(null)
  const [pending, setPending] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const shareUrl = created ? buildShareUrl(created) : null

  useEffect(() => {
    const previousFocus = document.activeElement as HTMLElement | null
    actionRef.current?.focus()
    return () => previousFocus?.focus()
  }, [])

  useEffect(() => {
    if (created) {
      shareUrlRef.current?.focus()
    }
  }, [created])

  const create = async () => {
    if (pending) return
    setPending(true)
    setError(null)
    setNotice(null)
    try {
      setCreated(await onCreate())
    } catch (createError) {
      setError(
        createError instanceof Error
          ? createError.message
          : '공유 링크를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
    } finally {
      setPending(false)
    }
  }

  const copy = async () => {
    if (!shareUrl) return
    setError(null)
    try {
      await navigator.clipboard.writeText(shareUrl)
      setNotice('공유 링크를 복사했습니다.')
    } catch {
      setError('링크를 직접 선택해 복사해 주세요.')
    }
  }

  const revoke = async () => {
    if (!created || pending) return
    setPending(true)
    setError(null)
    try {
      await onRevoke(created.shareToken)
      setCreated(null)
      setNotice('공유가 해제되었습니다.')
    } catch (revokeError) {
      setError(
        revokeError instanceof Error
          ? revokeError.message
          : '공유를 해제하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="report-dialog-backdrop">
      <section
        aria-labelledby="share-dialog-heading"
        aria-modal="true"
        className="report-dialog share-dialog"
        onKeyDown={(event) => {
          if (event.key === 'Escape' && !pending) onClose()
        }}
        role="dialog"
      >
        <h2 id="share-dialog-heading">결과 공유</h2>
        {created && shareUrl ? (
          <div className="share-dialog__result">
            <p>아래 링크는 생성 후 7일 동안 사용할 수 있습니다.</p>
            <label htmlFor="share-url">공유 링크</label>
            <input
              id="share-url"
              onFocus={(event) => event.currentTarget.select()}
              readOnly
              ref={shareUrlRef}
              value={shareUrl}
            />
            <div className="report-dialog__actions">
              <button type="button" onClick={() => void copy()}>
                링크 복사
              </button>
              <button
                className="text-action text-action--danger"
                disabled={pending}
                type="button"
                onClick={() => void revoke()}
              >
                {pending ? '해제 중' : '공유 해제'}
              </button>
            </div>
          </div>
        ) : (
          <p>
            {requiresSave
              ? '공유하려면 먼저 분석 결과를 저장해 주세요.'
              : '읽기 전용 공유 링크를 만듭니다.'}
          </p>
        )}
        {notice ? <p role="status">{notice}</p> : null}
        {error ? <p role="alert">{error}</p> : null}
        <div className="report-dialog__actions">
          <button disabled={pending} type="button" onClick={onClose}>
            닫기
          </button>
          {!created ? (
            <button
              className="primary-button"
              disabled={pending}
              ref={actionRef}
              type="button"
              onClick={() => void create()}
            >
              {pending
                ? '공유 준비 중'
                : requiresSave
                  ? '저장 후 공유'
                  : '공유 링크 만들기'}
            </button>
          ) : null}
        </div>
      </section>
    </div>
  )
}

export default ShareDialog
