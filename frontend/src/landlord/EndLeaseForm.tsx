import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError } from '../api/client'
import type { LeaseView } from '../api/types'
import { Button } from '../design/Button'
import { FormError, TextField } from '../design/Field'
import { formatDate, rupees, todayIso } from '../lib/format'
import styles from './EndLeaseForm.module.css'

/**
 * The tenant's last day. Today (or earlier) ends the lease now and frees the bed; a later day puts the
 * lease on notice until then. Asks once before doing it, on the page rather than in a browser dialog.
 */
export function EndLeaseForm({ lease, outstandingPaise }: { lease: LeaseView; outstandingPaise: number }) {
  const queryClient = useQueryClient()
  const firstName = lease.tenant.fullName.split(' ')[0]
  const onNotice = lease.status === 'NOTICE'
  const [lastDay, setLastDay] = useState(onNotice && lease.endsOn ? lease.endsOn : todayIso())
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string>()
  const question = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (confirming) question.current?.focus()
  }, [confirming])

  const endsNow = lastDay <= todayIso()
  const place = lease.unit.roomLabel ? `${lease.unit.label}, ${lease.unit.roomLabel}` : lease.unit.label

  const ask = (event: FormEvent) => {
    event.preventDefault()
    setFormError(undefined)
    if (!lastDay) {
      setFormError(`Choose ${firstName}'s last day.`)
      return
    }
    if (lastDay < lease.startsOn) {
      setFormError(`The last day can't be before ${firstName} moved in, on ${formatDate(lease.startsOn)}.`)
      return
    }
    setConfirming(true)
  }

  const end = async () => {
    setBusy(true)
    setFormError(undefined)
    try {
      await api(`/leases/${lease.id}/end`, { method: 'POST', json: { endsOn: lastDay } })
      setConfirming(false)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['lease', lease.id] }),
        queryClient.invalidateQueries({ queryKey: ['ledger', lease.id] }),
        queryClient.invalidateQueries({ queryKey: ['board'] }),
        queryClient.invalidateQueries({ queryKey: ['property'] }),
      ])
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={ask} noValidate>
      <p className={styles.note}>
        {onNotice && lease.endsOn
          ? `${firstName} moves out on ${formatDate(lease.endsOn)}. Change the day, or end it today.`
          : `When ${firstName} moves out, set the last day. A day ahead puts the lease on notice: rent stops after it, and ${place} is free from then.`}
      </p>
      <FormError>{formError}</FormError>
      <div className={styles.row}>
        <TextField
          label="Last day"
          type="date"
          min={lease.startsOn}
          value={lastDay}
          onChange={(event) => {
            setLastDay(event.target.value)
            setConfirming(false)
          }}
        />
        {!confirming && (
          <Button type="submit" variant="secondary">
            {onNotice ? 'Change the last day' : 'End this lease'}
          </Button>
        )}
      </div>
      {confirming && (
        <div className={styles.confirm} onKeyDown={(event) => event.key === 'Escape' && setConfirming(false)}>
          <p ref={question} tabIndex={-1} className={styles.question}>
            {endsNow
              ? `End ${firstName}'s lease now? ${place} shows as vacant at once, and you can invite someone new.`
              : `Put ${firstName} on notice until ${formatDate(lastDay)}? ${place} becomes vacant after that day.`}
          </p>
          {outstandingPaise > 0 && (
            <p className={styles.owed}>
              {firstName} still owes <span className="entry num">{rupees(outstandingPaise)}</span>. Settle it or record it
              first; once the lease ends, this page won't show on your register.
            </p>
          )}
          <p className={styles.email}>{firstName} gets an email about it.</p>
          <div className={styles.buttons}>
            <Button type="button" busy={busy} onClick={() => void end()}>
              {busy ? 'Saving' : endsNow ? 'End the lease' : 'Set the last day'}
            </Button>
            <Button type="button" variant="secondary" onClick={() => setConfirming(false)}>
              Keep it
            </Button>
          </div>
        </div>
      )}
    </form>
  )
}
