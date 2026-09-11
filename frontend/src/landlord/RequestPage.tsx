import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { Thread } from '../maintenance/Thread'
import { boardQuery } from './queries'
import styles from './RequestPage.module.css'

/** A request in the landlord's book, reached from its property's page or straight from the email about it. */
export function LandlordRequestPage() {
  const { ticketId = '', propertyId } = useParams()
  const board = useQuery(boardQuery)
  const property = board.data?.properties.find((entry) => entry.id === propertyId)
  return (
    <div className={styles.page}>
      <Link to={property ? `/l/p/${property.id}` : '/l'} className={styles.back}>
        {property ? `Back to ${property.name}` : 'Back to your book'}
      </Link>
      <Thread ticketId={ticketId} viewer="LANDLORD" />
    </div>
  )
}
