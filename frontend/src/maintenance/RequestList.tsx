import { Link } from 'react-router'
import type { TicketView } from '../api/types'
import { Mark } from '../design/Mark'
import { formatInstantShort } from '../lib/format'
import styles from './Requests.module.css'
import { TicketStatusMark } from './TicketMarks'

/** Requests as ruled lines: what's wrong and where, then its marks and when it last moved. */
export function RequestList({ tickets, linkTo, showTenant, label }: {
  tickets: TicketView[]
  linkTo: (ticket: TicketView) => string
  showTenant: boolean
  label: string
}) {
  return (
    <ul className={styles.list} aria-label={label}>
      {tickets.map((ticket) => {
        const unit = ticket.unit.roomLabel ? `${ticket.unit.label}, ${ticket.unit.roomLabel}` : ticket.unit.label
        return (
          <li key={ticket.id} className={styles.row}>
            <div className={styles.what}>
              <Link to={linkTo(ticket)} className={styles.title}>
                {ticket.title}
              </Link>
              <span className={styles.where}>
                {unit}
                {showTenant ? `, ${ticket.tenant.fullName}` : `, ${ticket.property.name}`}
              </span>
            </div>
            <div className={styles.state}>
              {ticket.priority === 'URGENT' && <Mark tone="urgent">Urgent</Mark>}
              <TicketStatusMark status={ticket.status} />
              <span className={styles.when}>{formatInstantShort(ticket.lastActivityAt)}</span>
            </div>
          </li>
        )
      })}
    </ul>
  )
}
