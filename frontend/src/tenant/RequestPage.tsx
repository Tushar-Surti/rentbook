import { Link, useParams } from 'react-router'
import { Thread } from '../maintenance/Thread'
import styles from './RequestsPage.module.css'

/** One of the tenant's requests: the thread they share with their landlord. */
export function TenantRequestPage() {
  const { ticketId = '' } = useParams()
  return (
    <div className={styles.page}>
      <Link to="/t/requests" className={styles.back}>
        All requests
      </Link>
      <Thread ticketId={ticketId} viewer="TENANT" />
    </div>
  )
}
