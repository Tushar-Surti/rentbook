import type { DepositSettlement } from '../api/types'
import { formatDate, methodPhrase, rupees } from '../lib/format'
import styles from './Deposit.module.css'

/**
 * The settlement as both parties read it: what was held, each deduction, and what comes back, closed by a
 * double rule like any printed account. Notes from either side sit under it in their own words.
 */
export function DepositStatement({ settlement, tenantName, landlordName }: {
  settlement: DepositSettlement
  tenantName: string
  landlordName: string
}) {
  const tenantFirst = tenantName.split(' ')[0]
  const landlordFirst = landlordName.split(' ')[0]
  return (
    <div className={styles.statement}>
      <table className={styles.account}>
        <caption className="visually-hidden">Deposit settlement</caption>
        <tbody>
          <tr className={styles.held}>
            <th scope="row">Deposit held</th>
            <td className="entry num">{rupees(settlement.heldPaise)}</td>
          </tr>
          {settlement.deductions.map((line, index) => (
            <tr key={`${line.description}-${index}`}>
              <th scope="row" className={styles.deduction}>
                {line.description}
                {line.chargeId && <span className={styles.fromBook}>Unpaid on the rent book</span>}
              </th>
              <td className="entry num">{rupees(-line.amountPaise).replace('-', '− ')}</td>
            </tr>
          ))}
          {settlement.deductions.length === 0 && (
            <tr>
              <th scope="row" className={styles.deduction}>
                No deductions
              </th>
              <td />
            </tr>
          )}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row">Back to {tenantFirst}</th>
            <td className={`${styles.refund} entry num`}>{rupees(settlement.refundPaise)}</td>
          </tr>
        </tfoot>
      </table>

      {settlement.landlordNote && (
        <div className={styles.note}>
          <p className={styles.noteBy}>{landlordFirst}'s note</p>
          <p className="entry">{settlement.landlordNote}</p>
        </div>
      )}
      {settlement.tenantNote && (
        <div className={styles.note}>
          <p className={styles.noteBy}>{tenantFirst}'s question</p>
          <p className="entry">{settlement.tenantNote}</p>
        </div>
      )}
      {settlement.refund && (
        <p className={styles.refunded}>
          Refunded <span className="entry num">{rupees(settlement.refundPaise)}</span>{' '}
          {methodPhrase(settlement.refund.method)} on <span className="entry">{formatDate(settlement.refund.refundedOn)}</span>
          {settlement.refund.reference && (
            <>
              , reference <span className="entry">{settlement.refund.reference}</span>
            </>
          )}
          .
        </p>
      )}
    </div>
  )
}
