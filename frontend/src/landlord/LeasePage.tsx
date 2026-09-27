import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { api } from '../api/client'
import type { LeaseView, LedgerEntry, VaultEntry } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Icon } from '../design/Icon'
import { LedgerTable } from '../ledger/LedgerTable'
import { useLedger } from '../ledger/useLedger'
import { formatDate, ordinal, rupees } from '../lib/format'
import { DocumentShelf } from '../documents/DocumentShelf'
import { FileDocument } from '../documents/FileDocument'
import { vaultQuery } from '../documents/queries'
import { ReceiptTable } from '../receipts/ReceiptTable'
import { receiptsQuery } from '../receipts/receipts'
import { LandlordDeposit } from '../deposit/LandlordDeposit'
import { LeaseConditionReports } from '../condition/ConditionReports'
import { AddChargeForm } from './AddChargeForm'
import { Addons } from './Addons'
import { EndLeaseForm } from './EndLeaseForm'
import { RecordPaymentForm } from './RecordPaymentForm'
import styles from './LeasePage.module.css'

const isOpen = (entry: LedgerEntry) => entry.status === 'DUE' || entry.status === 'OVERDUE' || entry.status === 'UPCOMING'

/** One tenancy in the book: who lives there, on what terms, and the ledger both of them read. */
export function LeasePage() {
  const { leaseId = '' } = useParams()
  const queryClient = useQueryClient()
  const lease = useQuery({ queryKey: ['lease', leaseId], queryFn: () => api<LeaseView>(`/leases/${leaseId}`) })
  const ledger = useLedger(leaseId)
  const receipts = useQuery(receiptsQuery(leaseId))
  const vault = useQuery(vaultQuery(leaseId))

  if (lease.isPending) return <SessionLoading />
  if (lease.isError) {
    return (
      <div className={styles.missing}>
        <h1 className={styles.title}>This lease isn't in your book</h1>
        <Link to="/l">Open your book</Link>
      </div>
    )
  }

  const view = lease.data
  const unit = view.unit.roomLabel ? `${view.unit.label}, ${view.unit.roomLabel}` : view.unit.label
  const removeDocument = async (entry: VaultEntry) => {
    await api(`/documents/${entry.id}`, { method: 'DELETE' })
    await queryClient.invalidateQueries({ queryKey: ['vault', leaseId] })
  }
  const waive = async (entry: LedgerEntry) => {
    await api(`/charges/${entry.id}/waive`, { method: 'POST' })
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['ledger', leaseId] }),
      queryClient.invalidateQueries({ queryKey: ['board'] }),
    ])
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>{view.tenant.fullName}</h1>
        <p className={styles.where}>
          {unit}, {view.property.name}
        </p>
        <p className={styles.contact}>
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

      {view.status === 'ENDED' && (
        <p className={styles.ended} role="status">
          This lease ended{view.endsOn ? ` on ${formatDate(view.endsOn)}` : ''}. {unit} is vacant again.
        </p>
      )}

      <dl className={styles.terms}>
        <div>
          <dt>Rent</dt>
          <dd className="entry num">{rupees(view.rentPaise)}</dd>
        </div>
        <div>
          <dt>Due on</dt>
          <dd className="entry">the {ordinal(view.dueDay)}</dd>
        </div>
        <div>
          <dt>Deposit</dt>
          <dd className="entry num">{view.depositPaise > 0 ? rupees(view.depositPaise) : 'None'}</dd>
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

      <section className={styles.section} aria-labelledby="ledger-heading">
        <h2 id="ledger-heading" className={styles.sectionHeading}>
          Rent book
        </h2>
        <p className={styles.note}>{view.tenant.fullName.split(' ')[0]} reads this same ledger, updated as you change it.</p>
        {ledger.data ? (
          <LedgerTable ledger={ledger.data} caption={`Charges on ${view.tenant.fullName}'s lease`} onWaive={waive} />
        ) : (
          <SessionLoading />
        )}
      </section>

      <section className={styles.section} aria-labelledby="condition-heading">
        <h2 id="condition-heading" className={styles.sectionHeading}>
          Condition
        </h2>
        <LeaseConditionReports lease={view} />
      </section>

      {view.status !== 'ACTIVE' && (
        <section className={styles.section} aria-labelledby="deposit-heading">
          <h2 id="deposit-heading" className={styles.sectionHeading}>
            Security deposit
          </h2>
          <LandlordDeposit lease={view} />
        </section>
      )}

      <section className={styles.section} aria-labelledby="receipts-heading">
        <h2 id="receipts-heading" className={styles.sectionHeading}>
          Receipts
        </h2>
        {receipts.data ? (
          <ReceiptTable
            receipts={receipts.data}
            caption={`Receipts issued to ${view.tenant.fullName}`}
            alignUnder="withActions"
          />
        ) : (
          <SessionLoading />
        )}
      </section>

      {view.status !== 'ENDED' && ledger.data && (
        <section className={styles.section} aria-labelledby="record-heading">
          <h2 id="record-heading" className={styles.sectionHeading}>
            Record a payment
          </h2>
          <p className={styles.note}>
            Paid you in cash, by UPI or another way? Tick what {view.tenant.fullName.split(' ')[0]} paid.
          </p>
          <RecordPaymentForm
            leaseId={leaseId}
            open={ledger.data.entries.filter(isOpen)}
          />
        </section>
      )}

      {view.status !== 'ENDED' && (
        <section className={styles.section} aria-labelledby="monthly-heading">
          <h2 id="monthly-heading" className={styles.sectionHeading}>
            Every month
          </h2>
          <Addons leaseId={leaseId} tenantFirstName={view.tenant.fullName.split(' ')[0]} />
        </section>
      )}

      {view.status !== 'ENDED' && (
        <section className={styles.section} aria-labelledby="charge-heading">
          <h2 id="charge-heading" className={styles.sectionHeading}>
            Add a charge
          </h2>
          <AddChargeForm leaseId={leaseId} />
        </section>
      )}

      <section className={styles.section} aria-labelledby="documents-heading">
        <h2 id="documents-heading" className={styles.sectionHeading}>
          Documents
        </h2>
        {vault.data ? (
          <DocumentShelf
            entries={vault.data}
            label={`Documents on ${view.tenant.fullName}'s lease`}
            onRemove={removeDocument}
          />
        ) : (
          <SessionLoading />
        )}
        <FileDocument leaseId={leaseId} viewer="LANDLORD" prominent={false} />
      </section>

      {view.status !== 'ENDED' && (
        <section className={styles.section} aria-labelledby="end-heading">
          <h2 id="end-heading" className={styles.sectionHeading}>
            {view.status === 'NOTICE' ? 'Moving out' : 'End this lease'}
          </h2>
          <EndLeaseForm lease={view} outstandingPaise={ledger.data?.outstandingPaise ?? 0} />
        </section>
      )}
    </div>
  )
}
