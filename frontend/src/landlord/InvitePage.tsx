import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useParams, useSearchParams } from 'react-router'
import { z } from 'zod'
import { api, ApiError } from '../api/client'
import type { IssuedInvite } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Button, LinkButton } from '../design/Button'
import { FormError, SelectField, TextField } from '../design/Field'
import { Sheet } from '../design/Sheet'
import { formatDate, ordinal, rupees, todayIso, toPaise } from '../lib/format'
import { indianMobile } from '../lib/validation'
import styles from './InvitePage.module.css'
import { boardQuery, propertyQuery } from './queries'
import { ShareLink } from './ShareLink'

const schema = z
  .object({
    tenantName: z.string().trim().min(1, "Enter the tenant's name").max(120),
    email: z.email("Enter the tenant's email; the invite goes there"),
    phone: indianMobile,
    rent: z.string().refine((value) => (toPaise(value) ?? 0) > 0, 'Enter the monthly rent'),
    deposit: z.string().refine((value) => value.trim() === '' || toPaise(value) !== null, 'Enter an amount, or leave it empty'),
    dueDay: z.string(),
    startsOn: z.string().min(1, 'Choose the move-in date'),
    endsOn: z.string(),
  })
  .refine((values) => !values.endsOn || values.endsOn > values.startsOn, {
    path: ['endsOn'],
    message: 'The lease must end after the move-in date',
  })

type Values = z.infer<typeof schema>

const SERVER_FIELDS: Record<string, keyof Values> = { rentPaise: 'rent', depositPaise: 'deposit' }

/** The landlord writes the terms on the original; the duplicate torn from it is what the tenant receives. */
export function InvitePage() {
  const { unitId = '' } = useParams()
  // Held here, not in the form: once sent, the board refreshes and marks this unit as invited.
  const [issued, setIssued] = useState<IssuedInvite>()
  const board = useQuery(boardQuery)
  const found = board.data?.properties
    .flatMap((property) => property.hooks.map((hook) => ({ property, hook })))
    .find((entry) => entry.hook.unitId === unitId)
  const property = useQuery({ ...propertyQuery(found?.property.id ?? ''), enabled: Boolean(found) })

  if (board.isPending || (found && property.isPending)) return <SessionLoading />
  if (!found) {
    return (
      <div className={styles.message}>
        <h1 className={styles.heading}>This unit isn't in your book</h1>
        <Link to="/l">Open your book</Link>
      </div>
    )
  }

  const unitName = found.hook.roomLabel ? `${found.hook.label}, ${found.hook.roomLabel}` : found.hook.label
  const back = `/l/p/${found.property.id}`

  if (issued) {
    return (
      <div className={styles.sent}>
        <h1 className={styles.heading}>Invite sent to {issued.invite.tenantName}</h1>
        <p className={styles.lede}>
          We emailed the link to <span className="entry">{issued.invite.email}</span>. You can also send it yourself.
        </p>
        <ShareLink issued={issued} />
        <Link to={back}>Back to {found.property.name}</Link>
      </div>
    )
  }

  if (found.hook.occupant || found.hook.invite) {
    return (
      <div className={styles.message}>
        <h1 className={styles.heading}>{unitName} isn't open for an invite</h1>
        <p>
          {found.hook.occupant
            ? `${found.hook.occupant.tenantName} lives here.`
            : `${found.hook.invite?.tenantName} already has an invite waiting.`}
        </p>
        <Link to={back}>Back to {found.property.name}</Link>
      </div>
    )
  }

  return (
    <InviteForm
      unitId={unitId}
      unitName={unitName}
      propertyName={found.property.name}
      back={back}
      defaultRentPaise={property.data?.units.find((unit) => unit.id === unitId)?.defaultRentPaise ?? null}
      onIssued={setIssued}
    />
  )
}

function InviteForm({ unitId, unitName, propertyName, back, defaultRentPaise, onIssued }: {
  unitId: string
  unitName: string
  propertyName: string
  back: string
  defaultRentPaise: number | null
  onIssued: (issued: IssuedInvite) => void
}) {
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string>()
  // Opened from a listing's enquiry: the enquirer's details and the listed terms come along.
  const [params] = useSearchParams()
  const { register, handleSubmit, formState, watch, setError } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      tenantName: params.get('name') ?? '',
      email: params.get('email') ?? '',
      phone: (params.get('phone') ?? '').replace(/\D/g, '').slice(-10),
      rent: params.get('rent') ?? (defaultRentPaise ? String(defaultRentPaise / 100) : ''),
      deposit: params.get('deposit') ?? '',
      dueDay: '5',
      startsOn: todayIso(),
      endsOn: '',
    },
  })
  const draft = watch()

  const submit = handleSubmit(async (values) => {
    setFormError(undefined)
    try {
      const result = await api<IssuedInvite>(`/units/${unitId}/invites`, {
        method: 'POST',
        json: {
          tenantName: values.tenantName,
          email: values.email,
          phone: values.phone ? `+91${values.phone}` : null,
          rentPaise: toPaise(values.rent),
          depositPaise: values.deposit.trim() ? toPaise(values.deposit) : 0,
          dueDay: Number(values.dueDay),
          startsOn: values.startsOn,
          endsOn: values.endsOn || null,
        },
      })
      onIssued(result)
      void queryClient.invalidateQueries({ queryKey: ['board'] })
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors).forEach(([field, message]) =>
          setError(SERVER_FIELDS[field] ?? (field as keyof Values), { message }),
        )
        setFormError(error.message)
      } else {
        setFormError('Could not reach Rentbook. Check your connection.')
      }
    }
  })

  const rentPaise = toPaise(draft.rent ?? '')
  const depositPaise = draft.deposit?.trim() ? toPaise(draft.deposit) : 0
  const tenant = draft.tenantName?.trim() || 'your tenant'

  return (
    <div className={styles.layout}>
      <div className={styles.intro}>
        <h1 className={styles.heading}>Invite a tenant to {unitName}</h1>
        <p className={styles.lede}>
          Write the terms on the original. The canary duplicate is what {tenant} receives, by email with a link to
          accept.
        </p>
      </div>

      <div className={styles.spread}>
        <Sheet className={styles.original} aria-labelledby="terms-form-heading">
          <h2 id="terms-form-heading" className={styles.partHeading}>
            The terms
          </h2>
          <form id="invite-form" className={styles.form} onSubmit={submit} noValidate aria-labelledby="terms-form-heading">
            <FormError>{formError}</FormError>
            <TextField label="Tenant's name" autoComplete="off" error={formState.errors.tenantName?.message} {...register('tenantName')} />
            <TextField
              label="Tenant's email"
              type="email"
              inputMode="email"
              autoComplete="off"
              error={formState.errors.email?.message}
              {...register('email')}
            />
            <TextField
              label="Tenant's mobile (optional)"
              prefix="+91"
              type="tel"
              inputMode="numeric"
              autoComplete="off"
              hint="Lets you send the link on WhatsApp too."
              error={formState.errors.phone?.message}
              {...register('phone')}
            />
            <div className={styles.pair}>
              <TextField label="Rent a month" prefix="₹" inputMode="decimal" error={formState.errors.rent?.message} {...register('rent')} />
              <TextField label="Deposit" prefix="₹" inputMode="decimal" error={formState.errors.deposit?.message} {...register('deposit')} />
            </div>
            <SelectField
              label="Rent due on"
              options={Array.from({ length: 28 }, (_, index) => ({ value: String(index + 1), label: `the ${ordinal(index + 1)} of each month` }))}
              {...register('dueDay')}
            />
            <div className={styles.pair}>
              <TextField
                label="Moving in"
                type="date"
                data-empty={draft.startsOn ? undefined : true}
                error={formState.errors.startsOn?.message}
                {...register('startsOn')}
              />
              <TextField
                label="Lease ends (optional)"
                type="date"
                data-empty={draft.endsOn ? undefined : true}
                error={formState.errors.endsOn?.message}
                {...register('endsOn')}
              />
            </div>
          </form>
        </Sheet>

        <Sheet tone="duplicate" className={styles.duplicate} aria-labelledby="duplicate-heading">
          <h2 id="duplicate-heading" className={styles.partHeading}>
            What {tenant} will see
          </h2>
          <dl className={styles.list}>
            <div>
              <dt>Where</dt>
              <dd className="entry">
                {unitName}, {propertyName}
              </dd>
            </div>
            <div>
              <dt>Rent</dt>
              <dd className="entry num">{rentPaise ? `${rupees(rentPaise)} a month` : ' '}</dd>
            </div>
            <div>
              <dt>Due</dt>
              <dd className="entry">on the {ordinal(Number(draft.dueDay || 5))}</dd>
            </div>
            <div>
              <dt>Deposit</dt>
              <dd className="entry num">{depositPaise ? rupees(depositPaise) : 'None'}</dd>
            </div>
            <div>
              <dt>Moving in</dt>
              <dd className="entry">{draft.startsOn ? formatDate(draft.startsOn) : ' '}</dd>
            </div>
            {draft.endsOn && (
              <div>
                <dt>Lease ends</dt>
                <dd className="entry">{formatDate(draft.endsOn)}</dd>
              </div>
            )}
          </dl>
        </Sheet>
      </div>

      <div className={styles.actions}>
        <Button type="submit" form="invite-form" busy={formState.isSubmitting}>
          {formState.isSubmitting ? 'Sending' : 'Send invite'}
        </Button>
        <LinkButton to={back} variant="quiet">
          Cancel
        </LinkButton>
      </div>
    </div>
  )
}
