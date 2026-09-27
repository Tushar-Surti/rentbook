import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, ApiError } from '../api/client'
import type { Addon } from '../api/types'
import { Button } from '../design/Button'
import { FormError, SelectField, TextField } from '../design/Field'
import { monthLabel, rupees, toPaise } from '../lib/format'
import { addonsQuery } from '../ledger/addons'
import styles from './Addons.module.css'


const PRESETS = [
  { value: 'Wi-Fi', label: 'Wi-Fi', kind: 'UTILITY' },
  { value: 'Meals', label: 'Meals', kind: 'OTHER' },
  { value: 'Parking', label: 'Parking', kind: 'OTHER' },
  { value: 'Laundry', label: 'Laundry', kind: 'OTHER' },
  { value: 'Maintenance', label: 'Maintenance charge', kind: 'OTHER' },
  { value: 'OTHER', label: 'Something else', kind: 'OTHER' },
] as const

const month = (offset: number) => {
  const now = new Date(new Date().toLocaleString('en-US', { timeZone: 'Asia/Kolkata' }))
  const date = new Date(now.getFullYear(), now.getMonth() + offset, 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

/** Charges that come every month with the rent, set up once: Wi-Fi, meals, parking, laundry. */
export function Addons({ leaseId, tenantFirstName }: { leaseId: string; tenantFirstName: string }) {
  const queryClient = useQueryClient()
  const addons = useQuery(addonsQuery(leaseId))
  const running = addons.data?.filter((addon) => addon.running) ?? []
  const [preset, setPreset] = useState<string>('Wi-Fi')
  const [label, setLabel] = useState('')
  const [amount, setAmount] = useState('')
  const [starts, setStarts] = useState(month(0))
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string>()
  const [added, setAdded] = useState('')

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['addons', leaseId] }),
      queryClient.invalidateQueries({ queryKey: ['ledger', leaseId] }),
      queryClient.invalidateQueries({ queryKey: ['board'] }),
    ])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(undefined)
    setAdded('')
    const choice = PRESETS.find((option) => option.value === preset) ?? PRESETS[0]
    const name = preset === 'OTHER' ? label.trim() : choice.value
    const amountPaise = toPaise(amount)
    if (!name) return setFormError('Say what the charge is for.')
    if (!amountPaise) return setFormError('Enter the amount each month.')
    setBusy(true)
    try {
      await api<Addon>(`/leases/${leaseId}/addons`, {
        method: 'POST',
        json: { kind: choice.kind, label: name, amountPaise, startsMonth: starts },
      })
      setAdded(`${name} is added every month from ${monthLabel(starts)}.`)
      setAmount('')
      setLabel('')
      await refresh()
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={styles.addons}>
      {running.length > 0 && (
        <ul className={styles.list}>
          {running.map((addon) => (
            <AddonRow key={addon.id} addon={addon} onStopped={refresh} />
          ))}
        </ul>
      )}
      <form className={styles.form} onSubmit={submit} noValidate>
        <FormError>{formError}</FormError>
        <div className={styles.fields}>
          <SelectField
            label="What"
            value={preset}
            onChange={(event) => setPreset(event.target.value)}
            options={PRESETS.map((option) => ({ value: option.value, label: option.label }))}
          />
          {preset === 'OTHER' && (
            <TextField label="Called" placeholder="Gym" maxLength={80} value={label} onChange={(event) => setLabel(event.target.value)} />
          )}
          <TextField label="Each month" prefix="₹" inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} />
          <SelectField
            label="Starting"
            value={starts}
            onChange={(event) => setStarts(event.target.value)}
            options={[0, 1, 2].map((offset) => ({ value: month(offset), label: monthLabel(month(offset)) }))}
          />
        </div>
        <div className={styles.actions}>
          <Button type="submit" variant="secondary" busy={busy}>
            {busy ? 'Adding' : 'Add every month'}
          </Button>
          <p className={styles.added} role="status">
            {added}
          </p>
        </div>
        <p className={styles.hint}>
          It goes on {tenantFirstName}'s rent book with each month's rent, due the same day. Stopping it keeps what's
          already billed.
        </p>
      </form>
    </div>
  )
}

function AddonRow({ addon, onStopped }: { addon: Addon; onStopped: () => Promise<unknown> }) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const stop = async () => {
    setBusy(true)
    try {
      await api(`/addons/${addon.id}/stop`, { method: 'POST' })
      await onStopped()
    } finally {
      setBusy(false)
    }
  }
  return (
    <li className={styles.row}>
      <span className={styles.what}>{addon.label}</span>
      <span className="entry num">{rupees(addon.amountPaise)} a month</span>
      <span className={styles.since}>from {monthLabel(addon.startsMonth.slice(0, 7))}</span>
      <span className={styles.action}>
        {!confirming ? (
          <Button variant="quiet" className={styles.rowAction} onClick={() => setConfirming(true)}>
            Stop
          </Button>
        ) : (
          <>
            <span className={styles.question}>Stop {addon.label}?</span>
            <Button variant="quiet" className={styles.rowAction} busy={busy} onClick={() => void stop()}>
              Stop it
            </Button>
            <Button variant="quiet" className={styles.rowAction} onClick={() => setConfirming(false)}>
              Keep it
            </Button>
          </>
        )}
      </span>
    </li>
  )
}
