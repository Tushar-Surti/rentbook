import { useQuery } from '@tanstack/react-query'
import { SessionLoading } from '../app/guards'
import { LinkButton } from '../design/Button'
import { firstName } from '../maintenance/labels'
import { ticketsQuery } from '../maintenance/queries'
import { RequestList } from '../maintenance/RequestList'
import listStyles from '../maintenance/Requests.module.css'
import { tenantHomeQuery } from './queries'
import styles from './RequestsPage.module.css'

/** Everything the tenant has asked to be fixed, newest activity first, and where to report the next thing. */
export function RequestsPage() {
  const home = useQuery(tenantHomeQuery)
  const tickets = useQuery(ticketsQuery())

  if (home.isPending || tickets.isPending) return <SessionLoading />
  const lease = home.data?.lease ?? null
  const canReport = lease !== null && lease.status !== 'ENDED'

  return (
    <div className={styles.page}>
      <div className={styles.intro}>
        <h1 className={styles.heading}>Requests</h1>
        <p className={styles.lede}>
          {lease
            ? `Tell ${firstName(lease.landlord.fullName)} what needs fixing. You both write on the same thread, and it updates as either of you adds to it.`
            : 'When you have an active lease, you can report problems here.'}
        </p>
        {canReport && (
          <div>
            <LinkButton to="/t/requests/new" variant="primary">
              Report a problem
            </LinkButton>
          </div>
        )}
      </div>
      {tickets.data && tickets.data.length > 0 ? (
        <RequestList
          tickets={tickets.data}
          linkTo={(ticket) => `/t/requests/${ticket.id}`}
          showTenant={false}
          label="Your requests"
        />
      ) : (
        <p className={listStyles.empty}>Nothing reported yet.</p>
      )}
    </div>
  )
}
