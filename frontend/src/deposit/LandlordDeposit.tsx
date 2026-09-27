import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { api, ApiError } from '../api/client'
import type { DepositView, LeaseView } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Button } from '../design/Button'
import { FormError, SelectField, TextAreaField, TextField } from '../design/Field'
import { rupees, todayIso, toPaise } from '../lib/format'
import { conditionLabel, conditionReportQuery, conditionReportsQuery } from '../condition/queries'
import styles from './Deposit.module.css'
import { DepositStatement } from './DepositStatement'

export function depositQuery(leaseId: string) {
  return { queryKey: ['deposit', leaseId], queryFn: () => api<DepositView>(`/leases/${leaseId}/deposit`) }
}

const unreachable = 'Could not reach Rentbook. Check your connection.'

/** The landlord's side of the deposit at move-out: propose, revise after a question, then record the refund. */
export function LandlordDeposit({ lease }: { lease: LeaseView }) {
  const deposit = useQuery(depositQuery(lease.id))
  const [editing, setEditing] = useState(false)
  const first = lease.tenant.fullName.split(' ')[0]

  if (deposit.isPending) return <SessionLoading />
  if (deposit.isError) return <p className={styles.hint}>The deposit couldn't be loaded. Reload the page to try again.</p>

  const view = deposit.data
  const settlement = view.settlement

  if (!settlement && view.heldPaise === 0) {
    return (
      <p className={styles.hint}>
        No deposit has been paid on this lease. If {first} paid you one directly, record it on the rent book above,
        then settle it here.
      </p>
    )
  }

  if (view.canPropose && (!settlement || editing || settlement.status === 'QUERIED')) {
    return (
      <div className={styles.statement}>
        {settlement?.status === 'QUERIED' && settlement.tenantNote && (
          <div className={styles.note}>
            <p className={styles.noteBy}>{first}'s question</p>
            <p className="entry">{settlement.tenantNote}</p>
          </div>
        )}
        <ProposeForm lease={lease} view={view} onDone={() => setEditing(false)} />
      </div>
    )
  }

  if (!settlement) return null

  return (
    <div className={styles.statement}>
      <DepositStatement settlement={settlement} tenantName={lease.tenant.fullName} landlordName={lease.landlord.fullName} />
      {settlement.status === 'PROPOSED' && (
        <div className={styles.actions}>
          <p className={styles.status}>Sent to {first}. Waiting for them to accept it or ask about it.</p>
          <Button variant="secondary" onClick={() => setEditing(true)}>
            Change it
          </Button>
        </div>
      )}
      {settlement.status === 'ACCEPTED' && <RefundForm lease={lease} refundPaise={settlement.refundPaise} />}
      {settlement.status === 'SETTLED' && !settlement.refund && (
        <p className={styles.status}>{first} accepted it. The deductions took the whole deposit, so there's nothing to refund.</p>
      )}
    </div>
  )
}

type Line = { key: number; description: string; amount: string }

/** What the sent move-out report found worse than at move-in: the grounds for a deduction. */
function useMoveOutFindings(lease: LeaseView) {
  const reports = useQuery(conditionReportsQuery(lease.id))
  const moveOut = reports.data?.find((report) => report.kind === 'MOVE_OUT' && report.status !== 'DRAFT')
  const report = useQuery({ ...conditionReportQuery(moveOut?.id ?? ''), enabled: Boolean(moveOut) })
  if (!report.data) return null
  return {
    worse: report.data.lines.filter((line) => line.worse),
    confirmed: report.data.status === 'CONFIRMED',
    link: `/l/p/${lease.property.id}/leases/${lease.id}/condition/${report.data.id}`,
  }
}

function ProposeForm({ lease, view, onDone }: { lease: LeaseView; view: DepositView; onDone: () => void }) {
  const queryClient = useQueryClient()
  const first = lease.tenant.fullName.split(' ')[0]
  const previous = view.settlement
  const [charges, setCharges] = useState<Set<string>>(
    () => new Set(previous?.deductions.flatMap((line) => (line.chargeId ? [line.chargeId] : [])) ?? []),
  )
  const [lines, setLines] = useState<Line[]>(() =>
    (previous?.deductions ?? [])
      .filter((line) => !line.chargeId)
      .map((line, index) => ({ key: index, description: line.description, amount: String(line.amountPaise / 100) })),
  )
  const [note, setNote] = useState(previous?.landlordNote ?? '')
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string>()
  const findings = useMoveOutFindings(lease)

  const fromBook = view.openCharges.filter((charge) => charges.has(charge.id))
  const deducted =
    fromBook.reduce((sum, charge) => sum + charge.amountPaise, 0) +
    lines.reduce((sum, line) => sum + (toPaise(line.amount) ?? 0), 0)
  const back = view.heldPaise - deducted

  const setLine = (key: number, change: Partial<Line>) =>
    setLines((current) => current.map((line) => (line.key === key ? { ...line, ...change } : line)))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(undefined)
    const custom = lines.filter((line) => line.description.trim() || line.amount.trim())
    if (custom.some((line) => !line.description.trim() || !(toPaise(line.amount) ?? 0))) {
      setFormError('Give each deduction what it is for and an amount, or remove it.')
      return
    }
    if (back < 0) {
      setFormError(`The deductions are more than the ${rupees(view.heldPaise)} held. Anything beyond it stays on the rent book.`)
      return
    }
    setBusy(true)
    try {
      await api(`/leases/${lease.id}/deposit`, {
        method: 'PUT',
        json: {
          deductions: [
            ...fromBook.map((charge) => ({ chargeId: charge.id })),
            ...custom.map((line) => ({ description: line.description.trim(), amountPaise: toPaise(line.amount) })),
          ],
          note: note.trim() || null,
        },
      })
      await queryClient.invalidateQueries({ queryKey: ['deposit', lease.id] })
      await queryClient.invalidateQueries({ queryKey: ['board'] })
      onDone()
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : unreachable)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      <FormError>{formError}</FormError>
      {view.openCharges.length > 0 && (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Still unpaid on the rent book</legend>
          {view.openCharges.map((charge) => (
            <label key={charge.id} className={styles.check}>
              <input
                type="checkbox"
                checked={charges.has(charge.id)}
                onChange={(event) =>
                  setCharges((current) => {
                    const next = new Set(current)
                    if (event.target.checked) next.add(charge.id)
                    else next.delete(charge.id)
                    return next
                  })
                }
              />
              <span>Take {charge.description} from the deposit</span>
              <span className="entry num">{rupees(charge.amountPaise)}</span>
            </label>
          ))}
        </fieldset>
      )}

      {findings && findings.worse.length > 0 && (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Worse than at move-in</legend>
          <p className={styles.hint}>
            From the <Link to={findings.link}>move-out report</Link>
            {findings.confirmed ? `, which ${first} confirmed` : `, still waiting for ${first} to confirm it`}.
          </p>
          {findings.worse.map((line) => {
            const description = `${line.item} (${line.area}), ${conditionLabel(line.condition).toLowerCase()}`
            const taken = lines.some((existing) => existing.description === description)
            return (
              <div key={line.id} className={styles.finding}>
                <span>
                  <span className={styles.findingWhat}>
                    {line.area}: {line.item}
                  </span>{' '}
                  {conditionLabel(line.atMoveIn!).toLowerCase()} at move-in, now{' '}
                  <span className="entry">{conditionLabel(line.condition).toLowerCase()}</span>
                  {line.note && <span className="entry">. {line.note}</span>}
                </span>
                {taken ? (
                  <span className={styles.hint}>Added below</span>
                ) : (
                  <Button
                    variant="quiet"
                    onClick={() => setLines((current) => [...current, { key: Date.now(), description, amount: '' }])}
                  >
                    Deduct for this
                  </Button>
                )}
              </div>
            )
          })}
        </fieldset>
      )}

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Other deductions</legend>
        <div className={styles.lines}>
          {lines.map((line) => (
            <div key={line.key} className={styles.line}>
              <TextField
                label="What for"
                placeholder="Repainting the room"
                maxLength={160}
                value={line.description}
                onChange={(event) => setLine(line.key, { description: event.target.value })}
              />
              <TextField
                label="Amount"
                prefix="₹"
                inputMode="decimal"
                value={line.amount}
                onChange={(event) => setLine(line.key, { amount: event.target.value })}
              />
              <Button variant="quiet" onClick={() => setLines((current) => current.filter((item) => item.key !== line.key))}>
                Remove
              </Button>
            </div>
          ))}
        </div>
        <div>
          <Button
            variant="secondary"
            onClick={() => setLines((current) => [...current, { key: Date.now(), description: '', amount: '' }])}
          >
            Add a deduction
          </Button>
        </div>
      </fieldset>

      <TextAreaField
        label="Note to the tenant (optional)"
        rows={3}
        maxLength={500}
        value={note}
        onChange={(event) => setNote(event.target.value)}
      />

      <dl className={styles.summary} aria-live="polite">
        <div>
          <dt>Deposit held</dt>
          <dd className="entry num">{rupees(view.heldPaise)}</dd>
        </div>
        <div>
          <dt>Deductions</dt>
          <dd className="entry num">{rupees(deducted)}</dd>
        </div>
        <div>
          <dt>Back to {first}</dt>
          <dd className="entry num">{rupees(Math.max(back, 0))}</dd>
        </div>
      </dl>

      <div className={styles.actions}>
        <Button type="submit" busy={busy}>
          {busy ? 'Sending' : `Send to ${first}`}
        </Button>
        <p className={styles.hint}>{first} gets an email to accept it or ask about it.</p>
      </div>
    </form>
  )
}

function RefundForm({ lease, refundPaise }: { lease: LeaseView; refundPaise: number }) {
  const queryClient = useQueryClient()
  const first = lease.tenant.fullName.split(' ')[0]
  const [method, setMethod] = useState('UPI')
  const [refundedOn, setRefundedOn] = useState(todayIso())
  const [reference, setReference] = useState('')
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string>()

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(undefined)
    setBusy(true)
    try {
      await api(`/leases/${lease.id}/deposit/refund`, {
        method: 'POST',
        json: { method, refundedOn, reference: reference.trim() || null },
      })
      await queryClient.invalidateQueries({ queryKey: ['deposit', lease.id] })
      await queryClient.invalidateQueries({ queryKey: ['board'] })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : unreachable)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      <p className={styles.status}>
        {first} accepted it. Pay back <span className="entry num">{rupees(refundPaise)}</span>, then record it here.
      </p>
      <FormError>{formError}</FormError>
      <div className={styles.line}>
        <SelectField
          label="How you paid it"
          value={method}
          onChange={(event) => setMethod(event.target.value)}
          options={[
            { value: 'UPI', label: 'UPI' },
            { value: 'BANK_TRANSFER', label: 'Bank transfer' },
            { value: 'CASH', label: 'Cash' },
            { value: 'CHEQUE', label: 'Cheque' },
          ]}
        />
        <TextField
          label="Paid on"
          type="date"
          max={todayIso()}
          value={refundedOn}
          onChange={(event) => setRefundedOn(event.target.value)}
        />
      </div>
      <TextField
        label="Reference (optional)"
        placeholder="UPI ref, cheque no."
        maxLength={200}
        value={reference}
        onChange={(event) => setReference(event.target.value)}
      />
      <div className={styles.actions}>
        <Button type="submit" busy={busy}>
          {busy ? 'Recording' : `Record the ${rupees(refundPaise)} refund`}
        </Button>
      </div>
    </form>
  )
}
