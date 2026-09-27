import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, Navigate, NavLink, Outlet } from 'react-router'
import type { PaymentEvent, TicketPush } from '../api/types'
import { SessionLoading } from '../app/guards'
import { useAuth, useCurrentUser } from '../auth/AuthProvider'
import { Brand } from '../design/Brand'
import { Button } from '../design/Button'
import { Icon } from '../design/Icon'
import styles from '../landlord/LandlordLayout.module.css'
import { rupees } from '../lib/format'
import { requestLiveLine } from '../maintenance/labels'
import { useLive } from '../realtime/LiveProvider'
import { caretakerBoardQuery } from './queries'

/**
 * A caretaker's book: the landlord's, cut down to the properties they look after. The same thumb-index
 * tabs and live line; no property to add, no payouts, no invites.
 */
export function CaretakerLayout() {
  const user = useCurrentUser()
  const { logout } = useAuth()
  const queryClient = useQueryClient()
  const board = useQuery(caretakerBoardQuery)
  const [latest, setLatest] = useState('')

  useLive<TicketPush | PaymentEvent>('/user/queue/events', (event) => {
    if (event.type.startsWith('ticket.')) {
      const push = event.data as TicketPush
      setLatest(requestLiveLine(event.type, push))
      void queryClient.invalidateQueries({ queryKey: ['tickets'] })
      void queryClient.invalidateQueries({ queryKey: ['ticket', push.ticketId] })
    }
    if (event.type === 'payment.confirmed') {
      const paid = event.data as PaymentEvent
      setLatest(`Recorded ${paid.tenantName ?? 'a tenant'}'s payment of ${rupees(paid.amountPaise)}.`)
      void queryClient.invalidateQueries({ queryKey: ['caretaker-board'] })
    }
  })

  if (board.isError) {
    return (
      <div className={styles.shell}>
        <main className={styles.page}>
          <h1>You no longer have caretaker access</h1>
          <p>Ask the landlord if this is a mistake.</p>
          <Button variant="secondary" onClick={() => void logout()}>
            Sign out
          </Button>
        </main>
      </div>
    )
  }

  return (
    <div className={styles.shell}>
      <header className={styles.topbar}>
        <Link to="/c" className={styles.home} aria-label="Rentbook, the properties you look after">
          <Brand />
        </Link>
        <div className={styles.who}>
          <span className={styles.name}>
            {user.fullName}
            {board.data && `, caretaker for ${board.data.landlordName}`}
          </span>
          <Button variant="quiet" onClick={() => void logout()}>
            <Icon name="door" />
            Sign out
          </Button>
        </div>
      </header>
      <p role="status" className={styles.latest}>
        {latest}
      </p>
      <div className={styles.body}>
        <nav aria-label="Properties" className={styles.tabs}>
          {board.data?.properties.map((property) => (
            <NavLink key={property.id} to={`/c/p/${property.id}`} className={styles.tab}>
              {property.name}
            </NavLink>
          ))}
        </nav>
        <main className={styles.page}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}

/** Opens the first property the caretaker looks after. */
export function CaretakerIndex() {
  const board = useQuery(caretakerBoardQuery)
  if (board.isPending) return <SessionLoading />
  const first = board.data?.properties[0]
  if (!first) {
    return <p>No properties are assigned to you yet. The landlord can add them from their Caretakers page.</p>
  }
  return <Navigate to={`/c/p/${first.id}`} replace />
}
