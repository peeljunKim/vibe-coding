import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('서비스 이름을 표시한다', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: '기사체크' })).toBeInTheDocument()
  })
})
