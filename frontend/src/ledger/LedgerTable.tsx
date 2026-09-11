import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../api/client'
import type { Ledger, LedgerEntry } from '../api/types'
import { Button } from '../design/Button'
import { Mark } from '../design/Mark'
import { Stamp } from '../design/Stamp'
import { formatDate, rupees } from '../lib/format'
import styles from './LedgerTable.module.css'

type Props = {
  ledger: Ledger
  caption: string
  /** Landlords may waive an unpaid charge; tenants see the same rows without the action. */
  onWaive?: (entry: LedgerEntry) => Promise<unknown>
}

const OPEN = new Set(['UPCOMING', 'DUE', 'OVERDUE'])

/** The rent book itself: every charge on a lease, ruled like a ledger, closed by what is outstanding. */
export function LedgerTable({ ledger, caption, onWaive }: Props) {
  if (ledger.entries.length === 0) {
    return <p className={styles.empty}>Nothing billed yet. Rent appears here ten days before it is due.</p>
  }
  return (
    <table className={styles.ledger}>
      <caption className="visually-hidden">{caption}</caption>
      <colgroup>
        <col className={styles.colDue} />
        <col />
        <col className={styles.colAmount} />
        <col className={styles.colStatus} />
        {onWaive && <col className={styles.colAction} />}
      </colgroup>
      <thead>
        <tr>
          <th scope="col">Due</th>
          <th scope="col">For</th>
          <th scope="col" className={styles.amount}>
            Amount
          </th>
          <th scope="col">Status</th>
          {onWaive && (
            <th scope="col">
              <span className="visually-hidden">Actions</span>
            </th>
          )}
        </tr>
      </thead>
      <tbody>
        {ledger.entries.map((entry) => (
          <Row key={entry.id} entry={entry} onWaive={onWaive} />
        ))}
      </tbody>
      <tfoot>
        <tr>
          <th scope="row" colSpan={2}>
            Outstanding
            {ledger.overduePaise > 0 && (
              <span className={styles.overdueNote}>{rupees(ledger.overduePaise)} of it overdue</span>
            )}
          </th>
          <td className={`${styles.amount} ${styles.total} entry num`}>{rupees(ledger.outstandingPaise)}</td>
          <td colSpan={onWaive ? 2 : 1} />
        </tr>
      </tfoot>
    </table>
  )
}

function Row({ entry, onWaive }: { entry: LedgerEntry; onWaive?: Props['onWaive'] }) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const waiveButton = useRef<HTMLButtonElement>(null)
  const question = useRef<HTMLSpanElement>(null)
  const returnFocus = useRef(false)
  const open = OPEN.has(entry.status)

  useEffect(() => {
    if (confirming) {
      question.current?.focus()
    } else if (returnFocus.current) {
      returnFocus.current = false
      waiveButton.current?.focus()
    }
  }, [confirming])

  const keep = () => {
    returnFocus.current = true
    setError(undefined)
    setConfirming(false)
  }

  const waive = async () => {
    setBusy(true)
    setError(undefined)
    try {
      await onWaive?.(entry)
      setConfirming(false)
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'That did not go through. Try again.')
    } finally {
      setBusy(false)
    }
  }

  const rowClass = [entry.status === 'WAIVED' ? styles.waived : '', confirming ? styles.confirming : '']
    .filter(Boolean)
    .join(' ')

  return (
    <>
      <tr className={rowClass || undefined}>
        <td className={`${styles.dueCell} entry`}>{formatDate(entry.dueOn)}</td>
        <td className={styles.forCell}>{entry.description}</td>
        <td className={`${styles.amount} ${styles.amountCell} entry num`}>{rupees(entry.amountPaise)}</td>
        <td className={styles.statusCell}>
          <EntryMark entry={entry} />
        </td>
        {onWaive && (
          <td className={styles.actionCell}>
            {open && !confirming && (
              <Button ref={waiveButton} variant="quiet" className={styles.rowAction} onClick={() => setConfirming(true)}>
                Waive
              </Button>
            )}
          </td>
        )}
      </tr>
      {/* The question gets a line of its own under the entry, starting where "For" starts. */}
      {onWaive && open && confirming && (
        <tr className={styles.confirmRow}>
          <td className={styles.confirmSpacer} />
          <td colSpan={4}>
            <span className={styles.confirm} onKeyDown={(event) => event.key === 'Escape' && keep()}>
              <span ref={question} tabIndex={-1} className={styles.question}>
                Waive {entry.description}?
              </span>
              <Button variant="quiet" className={styles.rowAction} busy={busy} onClick={() => void waive()}>
                Waive it
              </Button>
              <Button variant="quiet" className={styles.rowAction} onClick={keep}>
                Keep it
              </Button>
            </span>
            {error && (
              <span role="alert" className={styles.rowError}>
                {error}
              </span>
            )}
          </td>
        </tr>
      )}
    </>
  )
}

function EntryMark({ entry }: { entry: LedgerEntry }) {
  switch (entry.status) {
    case 'UPCOMING':
      return <Mark tone="upcoming">Upcoming</Mark>
    case 'DUE':
      return <Mark tone="due">Due today</Mark>
    case 'OVERDUE':
      return <Mark tone="overdue">Overdue</Mark>
    case 'PAID':
      return <Stamp>Paid</Stamp>
    case 'WAIVED':
      return <Mark tone="waived">Waived</Mark>
  }
}
