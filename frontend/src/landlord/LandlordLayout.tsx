import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router'
import type { DocumentPush, PaymentEvent, TicketPush } from '../api/types'
import { useAuth, useCurrentUser } from '../auth/AuthProvider'
import { Brand } from '../design/Brand'
import { Button } from '../design/Button'
import { Icon } from '../design/Icon'
import { rupees } from '../lib/format'
import { filedLine } from '../documents/labels'
import { requestLiveLine } from '../maintenance/labels'
import { useLive } from '../realtime/LiveProvider'
import styles from './LandlordLayout.module.css'
import { boardQuery } from './queries'

type MoveIn = { tenantName: string; unitId: string; leaseId: string }

/** The book: one thumb-index tab per property, cut into the edge of the open page. */
export function LandlordLayout() {
  const user = useCurrentUser()
  const { logout } = useAuth()
  const queryClient = useQueryClient()
  const board = useQuery(boardQuery)
  const [latest, setLatest] = useState('')

  useLive<MoveIn | PaymentEvent | TicketPush | DocumentPush>('/user/queue/events', (event) => {
    if (event.type === 'document.filed') {
      const filed = event.data as DocumentPush
      setLatest(filedLine(filed))
      void queryClient.invalidateQueries({ queryKey: ['vault', filed.leaseId] })
    }
    if (event.type.startsWith('ticket.')) {
      const push = event.data as TicketPush
      setLatest(requestLiveLine(event.type, push))
      void queryClient.invalidateQueries({ queryKey: ['tickets'] })
      void queryClient.invalidateQueries({ queryKey: ['ticket', push.ticketId] })
    }
    if (event.type === 'invite.accepted') {
      const moveIn = event.data as MoveIn
      setLatest(`${moveIn.tenantName} accepted your invite and has moved in.`)
      void queryClient.invalidateQueries({ queryKey: ['board'] })
      void queryClient.invalidateQueries({ queryKey: ['property'] })
    }
    if (event.type === 'payment.confirmed') {
      const paid = event.data as PaymentEvent
      setLatest(`Razorpay confirmed ${paid.tenantName ?? 'a tenant'}'s payment of ${rupees(paid.amountPaise)}.`)
      void queryClient.invalidateQueries({ queryKey: ['board'] })
      void queryClient.invalidateQueries({ queryKey: ['ledger', paid.leaseId] })
      void queryClient.invalidateQueries({ queryKey: ['receipts', paid.leaseId] })
    }
  })

  return (
    <div className={styles.shell}>
      <header className={styles.topbar}>
        <Link to="/l" className={styles.home} aria-label="Rentbook, your properties">
          <Brand />
        </Link>
        <div className={styles.who}>
          <NavLink to="/l/properties/new" className={`${styles.topLink} ${styles.addTop}`}>
            <Icon name="plus" size={18} />
            Add property
          </NavLink>
          <NavLink to="/l/payouts" className={styles.topLink}>
            Payouts
          </NavLink>
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
      <div className={styles.body}>
        <nav aria-label="Properties" className={styles.tabs}>
          {board.data?.properties.map((property) => (
            <NavLink key={property.id} to={`/l/p/${property.id}`} className={styles.tab}>
              {property.name}
            </NavLink>
          ))}
          <NavLink to="/l/properties/new" className={`${styles.tab} ${styles.addTab}`}>
            <Icon name="plus" size={18} />
            Add property
          </NavLink>
        </nav>
        <main className={styles.page}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
