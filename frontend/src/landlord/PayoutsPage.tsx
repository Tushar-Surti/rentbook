import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { api, ApiError } from '../api/client'
import type { Payouts } from '../api/types'
import { SessionLoading } from '../app/guards'
import { useCurrentUser } from '../auth/AuthProvider'
import { Button } from '../design/Button'
import { FormError, TextField } from '../design/Field'
import styles from './PayoutsPage.module.css'
import { payoutsQuery } from './queries'

const MOBILE = /^(?:\+?91)?[6-9]\d{9}$/

function schemaFor(needsPhone: boolean) {
  return z
    .object({
      legalName: z.string().trim().min(4, 'Enter your name as it is on your PAN').max(200),
      pan: z.string().trim().toUpperCase().regex(/^(?:[A-Z]{5}\d{4}[A-Z])?$/, 'Enter a PAN like ABCDE1234F'),
      phone: z.string().trim(),
      street: z.string().trim().min(1, 'Enter your street address').max(200),
      city: z.string().trim().min(1, 'Enter the city').max(80),
      state: z.string().trim().min(1, 'Enter the state').max(80),
      postalCode: z.string().trim().regex(/^[1-9]\d{5}$/, 'Enter a 6-digit PIN code'),
      beneficiaryName: z.string().trim().min(1, "Enter the account holder's name").max(120),
      accountNumber: z.string().trim().regex(/^\d{9,18}$/, 'Enter the account number, digits only'),
      confirmAccount: z.string().trim(),
      ifsc: z.string().trim().toUpperCase().regex(/^[A-Z]{4}0[A-Z0-9]{6}$/, 'Enter an IFSC like HDFC0001234'),
    })
    .refine((values) => !needsPhone || MOBILE.test(values.phone.replace(/[\s-]/g, '')), {
      path: ['phone'],
      message: 'Enter your 10-digit mobile number',
    })
    .refine((values) => values.accountNumber === values.confirmAccount, {
      path: ['confirmAccount'],
      message: "The two account numbers don't match",
    })
}

type Values = z.infer<ReturnType<typeof schemaFor>>

function percent(bps: number) {
  return `${bps / 100}%`
}

function statusLabel(status: string) {
  return status.replaceAll('_', ' ').toLowerCase()
}

/** Where a landlord connects a bank account, so tenants can pay rent online through Razorpay Route. */
export function PayoutsPage() {
  const payouts = useQuery(payoutsQuery)

  if (payouts.isPending) return <SessionLoading />
  if (payouts.isError) {
    return <p role="alert">Couldn't load your payout details. Reload to try again.</p>
  }
  const view = payouts.data

  return (
    <div className={styles.wrap}>
      <div className={styles.intro}>
        <h1 className={styles.heading}>{view.active ? 'Online rent is on' : 'Get paid online'}</h1>
        <p className={styles.lede}>
          Tenants pay through Razorpay, and your share goes straight to your bank account. Rentbook keeps{' '}
          {percent(view.platformFeeBps)} of each payment. This runs in Razorpay's test mode: no real money moves.
        </p>
      </div>
      {!view.paymentsConfigured ? (
        <p className={styles.note}>
          Online payments aren't switched on for this Rentbook yet, so your tenants keep paying you the way they do now.
        </p>
      ) : view.active ? (
        <Account view={view} />
      ) : (
        <>
          {view.status !== 'NOT_STARTED' && (
            <p className={styles.note}>
              Razorpay has your details. Its status for your account: {statusLabel(view.status)}. Send them again if
              anything has changed.
            </p>
          )}
          <OnboardingForm />
        </>
      )}
    </div>
  )
}

function Account({ view }: { view: Payouts }) {
  return (
    <dl className={styles.account}>
      <div>
        <dt>Paid out to</dt>
        <dd className="entry">{view.beneficiaryName}</dd>
      </div>
      <div>
        <dt>Bank account</dt>
        <dd>
          ending <span className="entry num">{view.bankLast4}</span>, IFSC <span className="entry">{view.ifsc}</span>
        </dd>
      </div>
      <div>
        <dt>At Razorpay</dt>
        <dd>Linked account {statusLabel(view.status)}</dd>
      </div>
    </dl>
  )
}

function OnboardingForm() {
  const user = useCurrentUser()
  const needsPhone = !user.phone
  const queryClient = useQueryClient()
  const schema = useMemo(() => schemaFor(needsPhone), [needsPhone])
  const [formError, setFormError] = useState<string>()
  const { register, handleSubmit, formState } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { legalName: user.fullName, beneficiaryName: user.fullName, pan: '', phone: '' },
  })
  const errors = formState.errors

  const submit = handleSubmit(async ({ confirmAccount, phone, pan, ...details }) => {
    void confirmAccount
    setFormError(undefined)
    try {
      const saved = await api<Payouts>('/payouts/account', {
        method: 'POST',
        json: {
          ...details,
          pan: pan || null,
          phone: needsPhone ? `+91${phone.replace(/\D/g, '').slice(-10)}` : null,
        },
      })
      queryClient.setQueryData(payoutsQuery.queryKey, saved)
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
    }
  })

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      <FormError>{formError}</FormError>
      <fieldset className={styles.group}>
        <legend className={styles.legend}>About you</legend>
        <TextField
          label="Name as on your PAN"
          autoComplete="name"
          error={errors.legalName?.message}
          {...register('legalName')}
        />
        <TextField
          label="PAN (optional)"
          hint="Printed on your tenants' rent receipts; they need it for HRA claims."
          autoCapitalize="characters"
          maxLength={10}
          error={errors.pan?.message}
          {...register('pan')}
        />
        {needsPhone && (
          <TextField
            label="Mobile number"
            type="tel"
            inputMode="tel"
            autoComplete="tel-national"
            prefix="+91"
            error={errors.phone?.message}
            {...register('phone')}
          />
        )}
        <TextField
          label="Street address"
          autoComplete="street-address"
          error={errors.street?.message}
          {...register('street')}
        />
        <div className={styles.pair}>
          <TextField label="City" autoComplete="address-level2" error={errors.city?.message} {...register('city')} />
          <TextField
            label="PIN code"
            inputMode="numeric"
            autoComplete="postal-code"
            maxLength={6}
            error={errors.postalCode?.message}
            {...register('postalCode')}
          />
        </div>
        <TextField label="State" autoComplete="address-level1" error={errors.state?.message} {...register('state')} />
      </fieldset>
      <fieldset className={styles.group}>
        <legend className={styles.legend}>Where your rent goes</legend>
        <TextField
          label="Account holder's name"
          error={errors.beneficiaryName?.message}
          {...register('beneficiaryName')}
        />
        <TextField
          label="Bank account number"
          inputMode="numeric"
          autoComplete="off"
          error={errors.accountNumber?.message}
          {...register('accountNumber')}
        />
        <TextField
          label="Account number again"
          inputMode="numeric"
          autoComplete="off"
          error={errors.confirmAccount?.message}
          {...register('confirmAccount')}
        />
        <TextField
          label="IFSC"
          hint="Test mode accepts any real-looking IFSC, such as HDFC0001234."
          autoCapitalize="characters"
          maxLength={11}
          error={errors.ifsc?.message}
          {...register('ifsc')}
        />
      </fieldset>
      <div>
        <Button type="submit" busy={formState.isSubmitting}>
          {formState.isSubmitting ? 'Setting up' : 'Set up payouts'}
        </Button>
      </div>
    </form>
  )
}
