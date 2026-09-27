import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import type { LedgerEntry } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Icon } from '../design/Icon'
import bookStyles from '../landlord/BookPage.module.css'
import { Register } from '../landlord/BookPage'
import leaseStyles from '../landlord/LeasePage.module.css'
import { RecordPaymentForm } from '../landlord/RecordPaymentForm'
import requestStyles from '../landlord/RequestPage.module.css'
import { LedgerTable } from '../ledger/LedgerTable'
import { useLedger } from '../ledger/useLedger'
import { formatDate, ordinal, rupees } from '../lib/format'
import { ticketsQuery } from '../maintenance/queries'
import { RequestList } from '../maintenance/RequestList'
import { Thread } from '../maintenance/Thread'
import { CARETAKER_API, caretakerBoardQuery, caretakerLeaseQuery } from './queries'

const EMPTY_UNITS = new Map()

/** One property in the caretaker's book: its register, read-only, and the requests that need them. */
export function CaretakerBookPage() {
  const { propertyId = '' } = useParams()
  const board = useQuery(caretakerBoardQuery)
  const requests = useQuery(ticketsQuery({ propertyId, open: true }, CARETAKER_API))

  if (board.isPending) return <SessionLoading />
  const page = board.data?.properties.find((property) => property.id === propertyId)
  if (!page) {
    return (
      <div className={bookStyles.missing}>
        <h1 className={bookStyles.title}>This property isn't one you look after</h1>
        <Link to="/c">Open your book</Link>
      </div>
    )
  }

  return (
    <div className={bookStyles.book}>
      <header className={bookStyles.header}>
        <h1 className={bookStyles.title}>{page.name}</h1>
        <p className={bookStyles.address}>{page.city}</p>
      </header>

      <section aria-labelledby="register-heading">
        <h2 id="register-heading" className="visually-hidden">
          Units and tenants
        </h2>
        {page.hooks.length === 0 ? (
          <div className={bookStyles.empty}>
            <p>No units here yet.</p>
          </div>
        ) : (
          <Register page={page} month={board.data?.month ?? ''} units={EMPTY_UNITS} base="/c" readOnly />
        )}
      </section>

      <section id="needs-you" className={bookStyles.needs} aria-labelledby="needs-heading">
        <h2 id="needs-heading" className={bookStyles.sectionHeading}>
          Needs you
        </h2>
        {requests.data && requests.data.length > 0 ? (
          <RequestList
            tickets={[...requests.data].sort(
              (a, b) => Number(b.priority === 'URGENT') - Number(a.priority === 'URGENT'),
            )}
            linkTo={(ticket) => `/c/p/${propertyId}/requests/${ticket.id}`}
            showTenant
            label={`Open requests at ${page.name}`}
          />
        ) : (
          <p className={bookStyles.empty}>No open requests.</p>
        )}
      </section>
    </div>
  )
}

const isOpen = (entry: LedgerEntry) => entry.status === 'DUE' || entry.status === 'OVERDUE' || entry.status === 'UPCOMING'

/** A tenancy as the caretaker sees it: the terms and the ledger, and rent paid to them in cash to record. */
export function CaretakerLeasePage() {
  const { leaseId = '', propertyId = '' } = useParams()
  const lease = useQuery(caretakerLeaseQuery(leaseId))
  const ledger = useLedger(leaseId, CARETAKER_API)

  if (lease.isPending) return <SessionLoading />
  if (lease.isError) {
    return (
      <div className={leaseStyles.missing}>
        <h1 className={leaseStyles.title}>This tenancy isn't one you look after</h1>
        <Link to="/c">Open your book</Link>
      </div>
    )
  }
  const view = lease.data
  const unit = view.unit.roomLabel ? `${view.unit.label}, ${view.unit.roomLabel}` : view.unit.label
  const first = view.tenant.fullName.split(' ')[0]

  return (
    <div className={leaseStyles.page}>
      <header className={leaseStyles.header}>
        <Link to={`/c/p/${propertyId}`} className={requestStyles.back}>
          Back to {view.property.name}
        </Link>
        <h1 className={leaseStyles.title}>{view.tenant.fullName}</h1>
        <p className={leaseStyles.where}>
          {unit}, {view.property.name}
        </p>
        <p className={leaseStyles.contact}>
          {view.tenant.phone && (
            <a href={`tel:${view.tenant.phone}`}>
              <Icon name="phone" size={18} />
              Call
            </a>
          )}
          <a href={`mailto:${view.tenant.email}`}>
            <Icon name="mail" size={18} />
            Email
          </a>
        </p>
      </header>

      <dl className={leaseStyles.terms}>
        <div>
          <dt>Rent</dt>
          <dd className="entry num">{rupees(view.rentPaise)}</dd>
        </div>
        <div>
          <dt>Due on</dt>
          <dd className="entry">the {ordinal(view.dueDay)}</dd>
        </div>
        <div>
          <dt>Moved in</dt>
          <dd className="entry">{formatDate(view.startsOn)}</dd>
        </div>
        <div>
          <dt>Lease ends</dt>
          <dd className="entry">{view.endsOn ? formatDate(view.endsOn) : 'No end date'}</dd>
        </div>
      </dl>

      <section className={leaseStyles.section} aria-labelledby="ledger-heading">
        <h2 id="ledger-heading" className={leaseStyles.sectionHeading}>
          Rent book
        </h2>
        {ledger.data ? (
          <LedgerTable ledger={ledger.data} caption={`Charges on ${view.tenant.fullName}'s lease`} />
        ) : (
          <SessionLoading />
        )}
      </section>

      {ledger.data && (
        <section className={leaseStyles.section} aria-labelledby="record-heading">
          <h2 id="record-heading" className={leaseStyles.sectionHeading}>
            Record a payment
          </h2>
          <p className={leaseStyles.note}>
            Paid you in cash or by UPI? Tick what {first} paid. The receipt says you recorded it for{' '}
            {view.landlord.fullName}.
          </p>
          <RecordPaymentForm leaseId={leaseId} open={ledger.data.entries.filter(isOpen)} base={CARETAKER_API} />
        </section>
      )}
    </div>
  )
}

/** A request on one of the caretaker's properties, from its page or straight from the email about it. */
export function CaretakerRequestPage() {
  const { ticketId = '', propertyId } = useParams()
  return (
    <div className={requestStyles.page}>
      <Link to={propertyId ? `/c/p/${propertyId}` : '/c'} className={requestStyles.back}>
        {propertyId ? 'Back to the property' : 'Back to your book'}
      </Link>
      <Thread ticketId={ticketId} viewer="CARETAKER" base={CARETAKER_API} />
    </div>
  )
}
