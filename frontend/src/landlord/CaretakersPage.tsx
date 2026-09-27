import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, ApiError } from '../api/client'
import type { CaretakerView, IssuedCaretaker, PropertyBoard } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Button } from '../design/Button'
import { FormError, TextField } from '../design/Field'
import { Icon } from '../design/Icon'
import { Mark } from '../design/Mark'
import { formatInstantShort } from '../lib/format'
import { indianMobile } from '../lib/validation'
import styles from './CaretakersPage.module.css'
import { boardQuery } from './queries'

const caretakersQuery = { queryKey: ['caretakers'], queryFn: () => api<CaretakerView[]>('/caretakers') }
const unreachable = 'Could not reach Rentbook. Check your connection.'

/**
 * The people who look after the landlord's properties. A caretaker sees the registers of the properties
 * assigned here, records rent paid to them in cash and handles repair requests; never documents,
 * payouts, invites, charges or deposits.
 */
export function CaretakersPage() {
  const board = useQuery(boardQuery)
  const caretakers = useQuery(caretakersQuery)
  const [issued, setIssued] = useState<IssuedCaretaker>()

  if (board.isPending || caretakers.isPending) return <SessionLoading />
  const properties = board.data?.properties ?? []

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Caretakers</h1>
        <p className={styles.lede}>
          A warden or manager who looks after some of your properties. They see who has paid there, record rent paid
          to them in cash, and handle repair requests. They never see documents, your payouts or deposits.
        </p>
      </header>

      {issued && (
        <section className={styles.issued} aria-labelledby="issued-heading">
          <h2 id="issued-heading" className={styles.sectionHeading}>
            Link for {issued.caretaker.fullName}
          </h2>
          <p className={styles.hint}>We've emailed it to {issued.caretaker.email}. You can also send it yourself.</p>
          <CopyLink issued={issued} />
          <Button variant="quiet" onClick={() => setIssued(undefined)}>
            Done
          </Button>
        </section>
      )}

      {caretakers.data && caretakers.data.length > 0 && (
        <section className={styles.section} aria-labelledby="team-heading">
          <h2 id="team-heading" className={styles.sectionHeading}>
            Your caretakers
          </h2>
          <ul className={styles.list}>
            {caretakers.data.map((caretaker) => (
              <CaretakerRow key={caretaker.id} caretaker={caretaker} properties={properties} onIssued={setIssued} />
            ))}
          </ul>
        </section>
      )}

      <section className={styles.section} aria-labelledby="invite-heading">
        <h2 id="invite-heading" className={styles.sectionHeading}>
          Add a caretaker
        </h2>
        {properties.length === 0 ? (
          <p className={styles.hint}>Add a property first; then choose which ones the caretaker looks after.</p>
        ) : (
          <InviteForm properties={properties} onIssued={setIssued} />
        )}
      </section>
    </div>
  )
}

function PropertyChoice({ properties, chosen, onChange }: {
  properties: PropertyBoard[]
  chosen: Set<string>
  onChange: (next: Set<string>) => void
}) {
  return (
    <fieldset className={styles.choice}>
      <legend className={styles.legend}>Properties they look after</legend>
      {properties.map((property) => (
        <label key={property.id} className={styles.check}>
          <input
            type="checkbox"
            checked={chosen.has(property.id)}
            onChange={(event) => {
              const next = new Set(chosen)
              if (event.target.checked) next.add(property.id)
              else next.delete(property.id)
              onChange(next)
            }}
          />
          {property.name}
        </label>
      ))}
    </fieldset>
  )
}

function InviteForm({ properties, onIssued }: { properties: PropertyBoard[]; onIssued: (issued: IssuedCaretaker) => void }) {
  const queryClient = useQueryClient()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [chosen, setChosen] = useState<Set<string>>(() => new Set(properties.length === 1 ? [properties[0].id] : []))
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string>()

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(undefined)
    const mobile = indianMobile.safeParse(phone)
    if (!fullName.trim() || !email.trim()) {
      setFormError('Give the caretaker a name and an email address.')
      return
    }
    if (!mobile.success) {
      setFormError(mobile.error.issues[0]?.message ?? 'Check the mobile number.')
      return
    }
    if (chosen.size === 0) {
      setFormError('Choose at least one property for them to look after.')
      return
    }
    setBusy(true)
    try {
      const issued = await api<IssuedCaretaker>('/caretakers', {
        method: 'POST',
        json: {
          fullName: fullName.trim(),
          email: email.trim(),
          phone: phone ? `+91${phone}` : null,
          propertyIds: [...chosen],
        },
      })
      onIssued(issued)
      setFullName('')
      setEmail('')
      setPhone('')
      await queryClient.invalidateQueries({ queryKey: ['caretakers'] })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : unreachable)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      <FormError>{formError}</FormError>
      <div className={styles.fields}>
        <TextField label="Name" autoComplete="off" value={fullName} onChange={(event) => setFullName(event.target.value)} />
        <TextField
          label="Email"
          type="email"
          inputMode="email"
          autoComplete="off"
          hint="Their own address, not one with a Rentbook account already."
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <TextField
          label="Mobile number (optional)"
          prefix="+91"
          type="tel"
          inputMode="numeric"
          value={phone}
          onChange={(event) => setPhone(event.target.value)}
        />
      </div>
      <PropertyChoice properties={properties} chosen={chosen} onChange={setChosen} />
      <div>
        <Button type="submit" busy={busy}>
          {busy ? 'Inviting' : 'Invite caretaker'}
        </Button>
      </div>
    </form>
  )
}

function CaretakerRow({ caretaker, properties, onIssued }: {
  caretaker: CaretakerView
  properties: PropertyBoard[]
  onIssued: (issued: IssuedCaretaker) => void
}) {
  const queryClient = useQueryClient()
  const [mode, setMode] = useState<'view' | 'edit' | 'remove'>('view')
  const [chosen, setChosen] = useState<Set<string>>(() => new Set(caretaker.properties.map((property) => property.id)))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true)
    setError(undefined)
    try {
      await action()
      await queryClient.invalidateQueries({ queryKey: ['caretakers'] })
      setMode('view')
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : unreachable)
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className={styles.row}>
      <div className={styles.who}>
        <span className={`${styles.name} entry`}>{caretaker.fullName}</span>
        <span className={styles.meta}>{caretaker.email}</span>
      </div>
      <div className={styles.where}>
        {caretaker.status === 'INVITED' ? (
          <Mark tone="invited">
            Invited{caretaker.inviteExpiresAt ? `, link expires ${formatInstantShort(caretaker.inviteExpiresAt)}` : ''}
          </Mark>
        ) : (
          <Mark tone="done">Active</Mark>
        )}
        <span className={styles.meta}>{caretaker.properties.map((property) => property.name).join(', ')}</span>
      </div>

      {mode === 'view' && (
        <div className={styles.actions}>
          <Button variant="quiet" className={styles.rowAction} onClick={() => setMode('edit')}>
            Change properties
          </Button>
          {caretaker.status === 'INVITED' && (
            <Button
              variant="quiet"
              className={styles.rowAction}
              busy={busy}
              onClick={() =>
                void run(async () => onIssued(await api<IssuedCaretaker>(`/caretakers/${caretaker.id}/resend`, { method: 'POST' })))
              }
            >
              New link
            </Button>
          )}
          <Button variant="quiet" className={styles.rowAction} onClick={() => setMode('remove')}>
            Remove
          </Button>
        </div>
      )}

      {mode === 'edit' && (
        <div className={styles.editing}>
          <PropertyChoice properties={properties} chosen={chosen} onChange={setChosen} />
          <div className={styles.actions}>
            <Button
              busy={busy}
              disabled={chosen.size === 0}
              onClick={() =>
                void run(() =>
                  api(`/caretakers/${caretaker.id}/properties`, { method: 'PUT', json: { propertyIds: [...chosen] } }),
                )
              }
            >
              Save
            </Button>
            <Button variant="secondary" onClick={() => setMode('view')}>
              Cancel
            </Button>
          </div>
        </div>
      )}

      {mode === 'remove' && (
        <div className={styles.editing}>
          <p className={styles.question}>
            Remove {caretaker.fullName}? They lose access straight away and are signed out everywhere.
          </p>
          <div className={styles.actions}>
            <Button busy={busy} onClick={() => void run(() => api(`/caretakers/${caretaker.id}`, { method: 'DELETE' }))}>
              Remove them
            </Button>
            <Button variant="secondary" onClick={() => setMode('view')}>
              Keep them
            </Button>
          </div>
        </div>
      )}

      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
    </li>
  )
}

function CopyLink({ issued }: { issued: IssuedCaretaker }) {
  const [copied, setCopied] = useState(false)
  const message = `Hi ${issued.caretaker.fullName}, here is your Rentbook caretaker invite: ${issued.link}`
  const phone = issued.caretaker.phone?.replace(/\D/g, '') ?? ''
  return (
    <div className={styles.copy}>
      <p className={`${styles.link} entry`}>{issued.link}</p>
      <div className={styles.actions}>
        <Button
          variant="secondary"
          onClick={() => void navigator.clipboard.writeText(issued.link).then(() => setCopied(true))}
        >
          <Icon name={copied ? 'check' : 'copy'} />
          {copied ? 'Copied' : 'Copy link'}
        </Button>
        <a
          className={styles.whatsapp}
          href={`https://wa.me/${phone}?text=${encodeURIComponent(message)}`}
          target="_blank"
          rel="noreferrer"
        >
          Send on WhatsApp
        </a>
      </div>
    </div>
  )
}
