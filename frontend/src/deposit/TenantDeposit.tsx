import { useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, ApiError } from '../api/client'
import type { DepositView, LeaseView } from '../api/types'
import { Button } from '../design/Button'
import { FormError, TextAreaField } from '../design/Field'
import { Sheet } from '../design/Sheet'
import { rupees } from '../lib/format'
import styles from './Deposit.module.css'
import { DepositStatement } from './DepositStatement'

const unreachable = 'Could not reach Rentbook. Check your connection.'

/** The tenant's copy of the deposit settlement: read it, then accept it or ask about it. */
export function TenantDeposit({ deposit, lease }: { deposit: DepositView; lease: LeaseView }) {
  const queryClient = useQueryClient()
  const settlement = deposit.settlement
  const landlord = lease.landlord.fullName.split(' ')[0]
  const [asking, setAsking] = useState(false)
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState<'accept' | 'ask'>()
  const [formError, setFormError] = useState<string>()

  const act = async (kind: 'accept' | 'ask') => {
    setFormError(undefined)
    setBusy(kind)
    try {
      await api(`/leases/${lease.id}/deposit/${kind === 'accept' ? 'accept' : 'query'}`, {
        method: 'POST',
        json: kind === 'ask' ? { note: question } : {},
      })
      setAsking(false)
      await queryClient.invalidateQueries({ queryKey: ['tenant-home'] })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : unreachable)
    } finally {
      setBusy(undefined)
    }
  }

  const ask = (event: FormEvent) => {
    event.preventDefault()
    if (!question.trim()) {
      setFormError(`Say what you'd like ${landlord} to look at again.`)
      return
    }
    void act('ask')
  }

  return (
    <Sheet className={styles.statement} aria-labelledby="deposit-heading">
      <h2 id="deposit-heading" className={styles.legend}>
        Your deposit
      </h2>
      {!settlement ? (
        <p>
          <span className="entry num">{rupees(deposit.heldPaise)}</span> is held as your deposit. {landlord} will send
          how it's settled after your last day.
        </p>
      ) : (
        <>
          <DepositStatement settlement={settlement} tenantName={lease.tenant.fullName} landlordName={lease.landlord.fullName} />
          <FormError>{formError}</FormError>
          {settlement.status === 'PROPOSED' && !asking && (
            <div className={styles.actions}>
              <Button busy={busy === 'accept'} onClick={() => void act('accept')}>
                {busy === 'accept' ? 'Accepting' : 'Accept'}
              </Button>
              <Button variant="secondary" onClick={() => setAsking(true)}>
                Ask about it
              </Button>
            </div>
          )}
          {settlement.status === 'PROPOSED' && asking && (
            <form className={styles.form} onSubmit={ask} noValidate>
              <TextAreaField
                label={`Your question for ${landlord}`}
                rows={3}
                maxLength={500}
                autoFocus
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
              />
              <div className={styles.actions}>
                <Button type="submit" busy={busy === 'ask'}>
                  {busy === 'ask' ? 'Sending' : 'Send your question'}
                </Button>
                <Button variant="secondary" onClick={() => setAsking(false)}>
                  Cancel
                </Button>
              </div>
            </form>
          )}
          {settlement.status === 'QUERIED' && (
            <p className={styles.status}>You asked about it. {landlord} will send an updated settlement.</p>
          )}
          {settlement.status === 'ACCEPTED' && (
            <p className={styles.status}>
              You accepted it. {landlord} will pay you back <span className="entry num">{rupees(settlement.refundPaise)}</span>.
            </p>
          )}
          {settlement.status === 'SETTLED' && !settlement.refund && (
            <p className={styles.status}>Settled. The deductions took the whole deposit.</p>
          )}
        </>
      )}
    </Sheet>
  )
}
