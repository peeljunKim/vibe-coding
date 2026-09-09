// 데스크톱 공통 머리글
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

interface AppHeaderProps {
  section: string
  children?: ReactNode
  onHome?: () => void
}

function AppHeader({ section, children, onHome }: AppHeaderProps) {
  return (
    <header className="site-header">
      <div className="site-header__inner">
        <Link className="site-header__brand" to="/" onClick={onHome}>
          기사체크
        </Link>
        <span className="site-header__divider" aria-hidden="true" />
        <span className="site-header__section">{section}</span>
        {children ? (
          <nav className="site-header__actions" aria-label="주요 메뉴">
            {children}
          </nav>
        ) : null}
      </div>
    </header>
  )
}

export default AppHeader
