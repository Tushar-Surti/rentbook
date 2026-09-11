import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useParams } from 'react-router'
import { z } from 'zod'
import { api, ApiError } from '../api/client'
import type { InvitePreview } from '../api/types'
import { SessionLoading } from '../app/guards'
import { useAuth } from '../auth/AuthProvider'
import { indianMobile } from '../lib/validation'
import { Brand } from '../design/Brand'
import { Button } from '../design/Button'
import { FormError, TextField } from '../design/Field'
import { Sheet } from '../design/Sheet'
import { formatDate, formatInstant, ordinal, rupees } from '../lib/format'
import styles from './AcceptInvitePage.module.css'

const newTenant = z.object({
  fullName: z.string().trim().min(1, 'Enter your name').max(120),
  phone: indianMobile,
  password: z.string().min(8, 'Use at least 8 characters').max(72, 'Use 72 characters or fewer'),
})

const returningTenant = z.object({
  fullName: z.string().optional(),
  phone: z.string().optional(),
  password: z.string().min(1, 'Enter your Rentbook password'),
})

type Values = z.infer<typeof newTenant>

function unitName(invite: InvitePreview) {
  return invite.roomLabel ? `${invite.unitLabel}, ${invite.roomLabel}` : invite.unitLabel
}

export function AcceptInvitePage() {
  const { token = '' } = useParams()
  const { state, logout } = useAuth()
  const preview = useQuery({
    queryKey: ['invite', token],
    queryFn: () => api<InvitePreview>(`/invites/${encodeURIComponent(token)}`),
  })

  if (state.status === 'signed-in' && state.user.role === 'TENANT' && preview.isError) {
    return <Navigate to="/t" replace />
  }

  return (
    <div className={styles.page}>
      <header>
        <Link to="/" className={styles.home} aria-label="Rentbook home">
          <Brand />
        </Link>
      </header>
      {preview.isPending && <SessionLoading />}
      {preview.isError && <InviteUnavailable error={preview.error} />}
      {preview.data && (
        <main className={styles.main}>
          <div className={styles.intro}>
            <h1 className={styles.heading}>
              {preview.data.landlordName} has invited you to rent {unitName(preview.data)} at{' '}
              {preview.data.propertyName}
            </h1>
          </div>
          <Terms invite={preview.data} />
          {state.status === 'signed-in' && state.user.email === preview.data.email ? (
            // Just accepted (or already living here): straight to the rent slip.
            <Navigate to="/t" replace />
          ) : state.status === 'signed-in' ? (
            <Sheet className={styles.formSheet}>
              <p>
                You're signed in as {state.user.fullName}. Sign out first, then open this link again to accept the
                invite.
              </p>
              <Button variant="secondary" onClick={() => void logout()}>
                Sign out
              </Button>
            </Sheet>
          ) : (
            <AcceptForm token={token} invite={preview.data} />
          )}
        </main>
      )}
    </div>
  )
}

function Terms({ invite }: { invite: InvitePreview }) {
  return (
    <Sheet tone="duplicate" className={styles.terms} aria-labelledby="terms-heading">
      <h2 id="terms-heading" className={styles.termsHeading}>
        The terms
      </h2>
      <dl className={styles.list}>
        <div>
          <dt>Rent</dt>
          <dd className="entry num">{rupees(invite.rentPaise)} a month</dd>
        </div>
        <div>
          <dt>Due</dt>
          <dd className="entry">on the {ordinal(invite.dueDay)} of each month</dd>
        </div>
        <div>
          <dt>Deposit</dt>
          <dd className="entry num">{invite.depositPaise > 0 ? rupees(invite.depositPaise) : 'None'}</dd>
        </div>
        <div>
          <dt>Moving in</dt>
          <dd className="entry">{formatDate(invite.startsOn)}</dd>
        </div>
        {invite.endsOn && (
          <div>
            <dt>Lease ends</dt>
            <dd className="entry">{formatDate(invite.endsOn)}</dd>
          </div>
        )}
        <div>
          <dt>Where</dt>
          <dd className="entry">
            {unitName(invite)}, {invite.propertyName}, {invite.city}
          </dd>
        </div>
      </dl>
      <p className={styles.expiry}>This invite works until {formatInstant(invite.expiresAt)}.</p>
    </Sheet>
  )
}

function AcceptForm({ token, invite }: { token: string; invite: InvitePreview }) {
  const { acceptInvite } = useAuth()
  const [formError, setFormError] = useState<string>()
  const { register, handleSubmit, formState } = useForm<Values>({
    resolver: zodResolver(invite.existingAccount ? returningTenant : newTenant) as never,
    defaultValues: { fullName: invite.tenantName, phone: '', password: '' },
  })

  const submit = handleSubmit(async ({ fullName, phone, password }) => {
    setFormError(undefined)
    try {
      await acceptInvite(token, invite.existingAccount ? { password } : {
        fullName,
        phone: phone ? `+91${phone}` : undefined,
        password,
      })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
    }
  })

  if (invite.existingAccount) {
    return (
      <Sheet className={styles.formSheet} aria-labelledby="accept-heading">
        <h2 id="accept-heading" className={styles.formHeading}>
          Add this home to your account
        </h2>
        <p className={styles.note}>
          You already have a Rentbook account as <span className="entry">{invite.email}</span>.
        </p>
        <form className={styles.form} onSubmit={submit} noValidate>
          <FormError>{formError}</FormError>
          <TextField
            label="Your Rentbook password"
            type="password"
            autoComplete="current-password"
            error={formState.errors.password?.message}
            {...register('password')}
          />
          <Button type="submit" block busy={formState.isSubmitting}>
            {formState.isSubmitting ? 'Accepting' : 'Accept invite'}
          </Button>
        </form>
      </Sheet>
    )
  }

  return (
    <Sheet className={styles.formSheet} aria-labelledby="accept-heading">
      <h2 id="accept-heading" className={styles.formHeading}>
        Accept and set up your account
      </h2>
      <p className={styles.note}>
        You'll sign in with <span className="entry">{invite.email}</span>.
      </p>
      <form className={styles.form} onSubmit={submit} noValidate>
        <FormError>{formError}</FormError>
        <TextField label="Your name" autoComplete="name" error={formState.errors.fullName?.message} {...register('fullName')} />
        <TextField
          label="Mobile number (optional)"
          prefix="+91"
          type="tel"
          autoComplete="tel-national"
          inputMode="numeric"
          hint="For rent reminders by SMS."
          error={formState.errors.phone?.message}
          {...register('phone')}
        />
        <TextField
          label="Choose a password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters."
          error={formState.errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" block busy={formState.isSubmitting}>
          {formState.isSubmitting ? 'Accepting' : 'Accept invite'}
        </Button>
      </form>
    </Sheet>
  )
}

function InviteUnavailable({ error }: { error: Error }) {
  const message =
    error instanceof ApiError && error.status === 410
      ? error.message
      : error instanceof ApiError && error.status === 404
        ? "This invite link isn't valid. Check that you opened the whole link, or ask your landlord to resend it."
        : 'Could not load this invite. Check your connection and reload.'
  return (
    <main className={styles.unavailable}>
      <h1 className={styles.heading}>This invite can't be used</h1>
      <p>{message}</p>
      <p>
        Already moved in? <Link to="/login">Sign in</Link>
      </p>
    </main>
  )
}
