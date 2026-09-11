import type { ReactNode } from 'react'
import { Navigate, useLocation, useSearchParams } from 'react-router'
import type { Role } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import styles from './guards.module.css'
import { homeFor } from './roles'

export function SessionLoading() {
  return (
    <div className={styles.loading} role="status">
      <span className="visually-hidden">Opening your rent book</span>
      <div className={styles.blank} aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
    </div>
  )
}

/** Only the given role gets through; everyone else is sent to sign in or to their own home. */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { state } = useAuth()
  const location = useLocation()
  if (state.status === 'loading') return <SessionLoading />
  if (state.status === 'signed-out') {
    return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />
  }
  if (state.user.role !== role) return <Navigate to={homeFor(state.user.role)} replace />
  return children
}

/** Sign-in and registration pages; a signed-in visitor goes where they were headed, or home. */
export function GuestOnly({ children }: { children: ReactNode }) {
  const { state } = useAuth()
  const [params] = useSearchParams()
  if (state.status === 'loading') return <SessionLoading />
  if (state.status === 'signed-in') {
    const home = homeFor(state.user.role)
    const next = params.get('next')
    return <Navigate to={next && next.startsWith(home) ? next : home} replace />
  }
  return children
}

export function HomeRedirect() {
  const { state } = useAuth()
  if (state.status === 'loading') return <SessionLoading />
  return <Navigate to={state.status === 'signed-in' ? homeFor(state.user.role) : '/login'} replace />
}
