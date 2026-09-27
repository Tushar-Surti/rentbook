import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, onSessionChange, refreshSession, setSession } from '../api/client'
import type { Me, SessionResponse } from '../api/types'

type AuthState = { status: 'loading' } | { status: 'signed-out' } | { status: 'signed-in'; user: Me }

type RegisterInput = { fullName: string; email: string; phone?: string; password: string; code: string }
type AcceptInput = { fullName?: string; phone?: string; password: string }

type AuthContextValue = {
  state: AuthState
  login: (email: string, password: string) => Promise<unknown>
  sendSignupCode: (email: string) => Promise<unknown>
  register: (input: RegisterInput) => Promise<unknown>
  sendPasswordResetCode: (email: string) => Promise<unknown>
  resetPassword: (input: { email: string; code: string; password: string }) => Promise<unknown>
  acceptInvite: (token: string, input: AcceptInput) => Promise<unknown>
  acceptCaretakerInvite: (token: string, input: { fullName?: string; password: string }) => Promise<unknown>
  logout: () => Promise<unknown>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  useEffect(() => {
    const stop = onSessionChange((session) =>
      setState(session ? { status: 'signed-in', user: session.user } : { status: 'signed-out' }),
    )
    // The refresh cookie, if the browser still holds one, restores the session on load.
    void refreshSession()
    return stop
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      login: (email, password) =>
        api<SessionResponse>('/auth/login', { method: 'POST', json: { email, password } }).then(setSession),
      sendSignupCode: (email) => api<void>('/auth/register/code', { method: 'POST', json: { email } }),
      register: (input) =>
        api<SessionResponse>('/auth/register', { method: 'POST', json: input }).then(setSession),
      sendPasswordResetCode: (email) => api<void>('/auth/password/code', { method: 'POST', json: { email } }),
      resetPassword: (input) =>
        api<SessionResponse>('/auth/password/reset', { method: 'POST', json: input }).then(setSession),
      acceptInvite: (token, input) =>
        api<SessionResponse>(`/invites/${encodeURIComponent(token)}/accept`, { method: 'POST', json: input }).then(
          setSession,
        ),
      acceptCaretakerInvite: (token, input) =>
        api<SessionResponse>(`/caretaker-invites/${encodeURIComponent(token)}/accept`, { method: 'POST', json: input }).then(
          setSession,
        ),
      logout: () => api<void>('/auth/logout', { method: 'POST' }).finally(() => setSession(null)),
    }),
    [state],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return context
}

export function useCurrentUser(): Me {
  const { state } = useAuth()
  if (state.status !== 'signed-in') {
    throw new Error('useCurrentUser needs a signed-in user')
  }
  return state.user
}
