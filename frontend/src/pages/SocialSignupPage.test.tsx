// 소셜 초대 가입 화면 검증
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { completeSocialSignup } from '../api/socialSignup'
import SocialSignupPage from './SocialSignupPage'

vi.mock('../api/socialSignup', () => ({
  completeSocialSignup: vi.fn(),
}))

describe('SocialSignupPage', () => {
  beforeEach(() => {
    vi.mocked(completeSocialSignup).mockReset()
  })

  it('초대 코드와 필수 동의만 입력받아 인증 Session을 전달한다', async () => {
    vi.mocked(completeSocialSignup).mockResolvedValue({
      authenticated: true,
      userId: '42',
      role: 'USER',
      expiresInSeconds: 7200,
    })
    const onAuthenticated = vi.fn()
    render(
      <MemoryRouter>
        <SocialSignupPage
          onLogin={vi.fn()}
          onAuthenticated={onAuthenticated}
        />
      </MemoryRouter>,
    )

    expect(screen.queryByLabelText('사용자 아이디')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('비밀번호')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('휴대전화 번호')).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('초대 코드'), {
      target: { value: 'INVITE-2026' },
    })
    fireEvent.click(
      screen.getByRole('checkbox', {
        name: '개인정보 처리와 서비스 이용약관에 동의합니다.',
      }),
    )
    fireEvent.click(screen.getByRole('button', { name: '가입 완료' }))

    await waitFor(() => {
      expect(completeSocialSignup).toHaveBeenCalledWith({
        inviteCode: 'INVITE-2026',
        agreementsAccepted: true,
      })
      expect(onAuthenticated).toHaveBeenCalledWith(
        expect.objectContaining({ authenticated: true, userId: '42' }),
      )
    })
  })
})
