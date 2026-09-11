import { useQuery } from '@tanstack/react-query'
import { SessionLoading } from '../app/guards'
import { Sheet } from '../design/Sheet'
import { LedgerTable } from '../ledger/LedgerTable'
import { useLedger } from '../ledger/useLedger'
import { ReceiptTable } from '../receipts/ReceiptTable'
import { receiptsQuery } from '../receipts/receipts'
import styles from './RentPage.module.css'
import { tenantHomeQuery } from './queries'

/** The tenant's copy of the rent book: every charge on the lease, the same rows the landlord sees. */
export function RentPage() {
  const home = useQuery(tenantHomeQuery)
  const lease = home.data?.lease ?? null
  const ledger = useLedger(lease?.id ?? null)
  const receipts = useQuery({ ...receiptsQuery(lease?.id ?? ''), enabled: Boolean(lease) })

  if (home.isPending) return <SessionLoading />
  if (!lease) {
    return (
      <div className={styles.page}>
        <h1 className={styles.heading}>No active lease</h1>
      </div>
    )
  }
  const landlord = lease.landlord.fullName

  return (
    <div className={styles.page}>
      <div className={styles.intro}>
        <h1 className={styles.heading}>Your rent book</h1>
        <p className={styles.lede}>
          Every charge on your lease. {landlord.split(' ')[0]} reads this same book, so you both see the same thing.
        </p>
      </div>
      <Sheet className={styles.sheet} aria-labelledby="charges-heading">
        <h2 id="charges-heading" className="visually-hidden">
          Charges
        </h2>
        {ledger.data ? <LedgerTable ledger={ledger.data} caption="Charges on your lease" /> : <SessionLoading />}
      </Sheet>
      <Sheet className={styles.sheet} aria-labelledby="receipts-heading">
        <h2 id="receipts-heading" className={styles.sheetHeading}>
          Receipts
        </h2>
        {receipts.data ? (
          <ReceiptTable receipts={receipts.data} caption="Receipts for your payments" />
        ) : (
          <SessionLoading />
        )}
      </Sheet>
    </div>
  )
}
