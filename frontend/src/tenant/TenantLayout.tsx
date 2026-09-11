import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router'
import type { DocumentPush, LedgerEvent, PaymentEvent, TicketPush } from '../api/types'
import { useAuth, useCurrentUser } from '../auth/AuthProvider'
import { Brand } from '../design/Brand'
import { Button } from '../design/Button'
import { Icon } from '../design/Icon'
import { rupees } from '../lib/format'
import { filedLine } from '../documents/labels'
import { requestLiveLine } from '../maintenance/labels'
import { useLive } from '../realtime/LiveProvider'
import styles from './TenantLayout.module.css'
import { tenantHomeQuery } from './queries'

type BookChange = Partial<LedgerEvent> & Partial<PaymentEvent>

/** Says who changed the shared book and what they changed, so a new number never arrives unexplained. */
function describe(type: string, change: BookChange): string | null {
  const amount = rupees(change.amountPaise ?? 0)
  const who = change.actor ? change.actor.trim().split(/\s+/)[0] : null
  if (type === 'charge.added') {
    return who
      ? `${who} added ${change.description}, ${amount}.`
      : `${change.description}, ${amount}, is now on your rent book.`
  }
  if (type === 'charge.waived') {
    return `${who ?? 'Your landlord'} waived ${change.description}.`
  }
  if (type === 'payment.confirmed') {
    return `Razorpay confirmed your payment of ${amount}. Receipt ${change.receiptNumber} is ready.`
  }
  return null
}

export function TenantLayout() {
  const user = useCurrentUser()
  const { logout } = useAuth()
  const queryClient = useQueryClient()
  const home = useQuery(tenantHomeQuery)
  const leaseId = home.data?.lease?.id ?? null
  const [latest, setLatest] = useState('')

  useLive<BookChange>(leaseId ? `/topic/leases/${leaseId}` : null, (event) => {
    const message = describe(event.type, event.data)
    if (message) setLatest(message)
  })

  // The landlord's replies and status changes on the tenant's requests, and documents they file.
  useLive<TicketPush | DocumentPush>('/user/queue/events', (event) => {
    if (event.type === 'document.filed') {
      setLatest(filedLine(event.data as DocumentPush))
      void queryClient.invalidateQueries({ queryKey: ['vault'] })
      return
    }
    if (!event.type.startsWith('ticket.')) return
    const push = event.data as TicketPush
    setLatest(requestLiveLine(event.type, push))
    void queryClient.invalidateQueries({ queryKey: ['tickets'] })
    void queryClient.invalidateQueries({ queryKey: ['ticket', push.ticketId] })
  })

  return (
    <div className={styles.shell}>
      <header className={styles.topbar}>
        <Link to="/t" className={styles.home} aria-label="Rentbook, your home">
          <Brand />
        </Link>
        <div className={styles.who}>
          <span className={styles.name}>{user.fullName}</span>
          <Button variant="quiet" onClick={() => void logout()}>
            <Icon name="door" />
            Sign out
          </Button>
        </div>
      </header>
      <p role="status" className={styles.latest}>
        {latest}
      </p>
      <nav aria-label="Your rent book" className={styles.tabs}>
        <NavLink to="/t" end className={styles.tab}>
          Home
        </NavLink>
        <NavLink to="/t/rent" className={styles.tab}>
          Rent
        </NavLink>
        <NavLink to="/t/requests" className={styles.tab}>
          Requests
        </NavLink>
        <NavLink to="/t/documents" className={styles.tab}>
          Documents
        </NavLink>
      </nav>
      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  )
}
