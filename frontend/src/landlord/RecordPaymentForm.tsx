import { useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, ApiError } from '../api/client'
import type { LedgerEntry, RecordedPayment } from '../api/types'
import { Button } from '../design/Button'
import { FormError, SelectField, TextField } from '../design/Field'
import { formatDate, rupees, todayIso } from '../lib/format'
import styles from './RecordPaymentForm.module.css'

const METHODS = [
  { value: 'CASH', label: 'Cash' },
  { value: 'UPI', label: 'UPI to my account' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'CHEQUE', label: 'Cheque' },
]

/**
 * Rent the tenant paid the landlord directly. The ticked charges become Paid for both of them, and the
 * tenant gets a receipt that says the landlord recorded it.
 */
export function RecordPaymentForm({ leaseId, open, base = '' }: { leaseId: string; open: LedgerEntry[]; base?: string }) {
  const queryClient = useQueryClient()
  // What's due now starts ticked; next month's rent, not yet due, is there to tick if they paid ahead.
  const [picked, setPicked] = useState<Set<string>>(
    () => new Set(open.filter((entry) => entry.status !== 'UPCOMING').map((entry) => entry.id)),
  )
  const [method, setMethod] = useState('CASH')
  const [receivedOn, setReceivedOn] = useState(todayIso())
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string>()
  const [recorded, setRecorded] = useState('')

  const chosen = open.filter((entry) => picked.has(entry.id))
  const total = chosen.reduce((sum, entry) => sum + entry.amountPaise, 0)

  const toggle = (id: string, on: boolean) =>
    setPicked((current) => {
      const next = new Set(current)
      if (on) next.add(id)
      else next.delete(id)
      return next
    })

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(undefined)
    setRecorded('')
    if (chosen.length === 0) {
      setFormError('Tick what the tenant paid.')
      return
    }
    if (!receivedOn || receivedOn > todayIso()) {
      setFormError("Choose the day you received it. It can't be in the future.")
      return
    }
    setBusy(true)
    try {
      const result = await api<RecordedPayment>(`${base}/leases/${leaseId}/payments`, {
        method: 'POST',
        json: { chargeIds: chosen.map((entry) => entry.id), method, receivedOn, note: note.trim() || null },
      })
      setRecorded(`Recorded ${rupees(result.amountPaise)}. Receipt ${result.receiptNumber} is issued.`)
      setNote('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['ledger', leaseId] }),
        queryClient.invalidateQueries({ queryKey: ['receipts', leaseId] }),
        queryClient.invalidateQueries({ queryKey: ['board'] }),
      ])
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
    } finally {
      setBusy(false)
    }
  }

  if (open.length === 0) {
    return (
      <p className={styles.settled} role="status">
        {recorded || 'Nothing is due right now.'}
      </p>
    )
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      <FormError>{formError}</FormError>
      <fieldset className={styles.charges}>
        <legend className={styles.legend}>What was paid</legend>
        {open.map((entry) => (
          <label key={entry.id} className={styles.charge}>
            <input
              type="checkbox"
              checked={picked.has(entry.id)}
              onChange={(event) => toggle(entry.id, event.target.checked)}
            />
            <span className={styles.what}>
              {entry.description}
              <span className={styles.due}>due {formatDate(entry.dueOn)}</span>
            </span>
            <span className={`${styles.amount} entry num`}>{rupees(entry.amountPaise)}</span>
          </label>
        ))}
      </fieldset>
      <div className={styles.fields}>
        <SelectField label="How" options={METHODS} value={method} onChange={(event) => setMethod(event.target.value)} />
        <TextField
          label="Received on"
          type="date"
          max={todayIso()}
          value={receivedOn}
          onChange={(event) => setReceivedOn(event.target.value)}
        />
        <TextField
          label="Note (optional)"
          placeholder="UPI ref, cheque no."
          maxLength={200}
          autoComplete="off"
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />
      </div>
      <div className={styles.actions}>
        <Button type="submit" busy={busy} disabled={chosen.length === 0}>
          {busy ? 'Recording' : `Record ${rupees(total)} as paid`}
        </Button>
        <p className={styles.recorded} role="status">
          {recorded}
        </p>
      </div>
      <p className={styles.hint}>The tenant gets a receipt that says you recorded it. Rentbook takes no fee on it.</p>
    </form>
  )
}
