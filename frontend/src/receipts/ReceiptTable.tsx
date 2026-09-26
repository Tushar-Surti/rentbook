import type { Receipt } from '../api/types'
import ledger from '../ledger/LedgerTable.module.css'
import { formatInstantDate, rupees } from '../lib/format'
import { ReceiptDownload } from './ReceiptDownload'
import styles from './Receipts.module.css'

/**
 * Every receipt on a lease, ruled like the ledger whose lines they settle. Both parties read the same list.
 * Under a ledger with an actions column, {@code alignUnder} puts Download in the same column as its
 * row actions, so the amounts and the actions line up down the page.
 */
export function ReceiptTable({ receipts, caption, alignUnder = 'plain' }: {
  receipts: Receipt[]
  caption: string
  alignUnder?: 'plain' | 'withActions'
}) {
  if (receipts.length === 0) {
    return <p className={ledger.empty}>No receipts yet. Each confirmed or recorded payment gets one here.</p>
  }
  const actions = alignUnder === 'withActions'
  return (
    <table className={ledger.ledger}>
      <caption className="visually-hidden">{caption}</caption>
      <colgroup>
        <col className={ledger.colDue} />
        <col />
        <col className={ledger.colAmount} />
        <col className={ledger.colStatus} />
        {actions && <col className={ledger.colAction} />}
      </colgroup>
      <thead>
        <tr>
          <th scope="col">Issued</th>
          <th scope="col">Receipt</th>
          <th scope="col" className={ledger.amount}>
            Amount
          </th>
          {actions && <td />}
          <th scope="col">
            <span className="visually-hidden">PDF</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {receipts.map((receipt) => {
          const download = (
            <ReceiptDownload receipt={receipt} className={ledger.rowAction}>
              Download
            </ReceiptDownload>
          )
          return (
            <tr key={receipt.id}>
              <td className={`${ledger.dueCell} entry`}>{formatInstantDate(receipt.issuedAt)}</td>
              <td className={ledger.forCell}>
                Receipt <span className="entry num">{receipt.number}</span>
                <span className={styles.items}>{receipt.items.join(', ')}</span>
              </td>
              <td className={`${ledger.amount} ${ledger.amountCell} entry num`}>{rupees(receipt.amountPaise)}</td>
              {actions ? (
                <>
                  <td className={ledger.statusCell} />
                  <td className={ledger.actionCell}>{download}</td>
                </>
              ) : (
                <td className={ledger.statusCell}>{download}</td>
              )}
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
