import { useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { api, ApiError } from '../api/client'
import type { PublicListing } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Brand } from '../design/Brand'
import { Button } from '../design/Button'
import { FormError, TextAreaField, TextField } from '../design/Field'
import { Sheet } from '../design/Sheet'
import { formatDate, rupees, todayIso } from '../lib/format'
import { offerName } from './place'
import styles from './PublicListing.module.css'

/**
 * A vacancy on a page of its own, for whoever the landlord shares the link with. It reads like the notice
 * on a gate: what's free, what it costs, from when, and who to ask. The street address stays private.
 */
export function PublicListingPage() {
  const { slug = '' } = useParams()
  const listing = useQuery({
    queryKey: ['public-listing', slug],
    queryFn: () => api<PublicListing>(`/public/listings/${encodeURIComponent(slug)}`),
    retry: false,
  })

  return (
    <div className={styles.page}>
      <header className={styles.top}>
        <Link to="/" className={styles.home} aria-label="Rentbook home">
          <Brand />
        </Link>
      </header>
      <main className={styles.main}>
        {listing.isPending ? (
          <SessionLoading />
        ) : listing.isError || !listing.data.open ? (
          <Sheet className={styles.sheet} aria-labelledby="let-heading">
            <h1 id="let-heading" className={styles.heading}>
              {listing.isError ? "This listing isn't here" : 'This place has been let'}
            </h1>
            <p className={styles.lede}>
              {listing.isError
                ? 'The link may be mistyped. Ask whoever shared it for a new one.'
                : 'Someone has moved in, so the listing is closed.'}
            </p>
          </Sheet>
        ) : (
          <Listing listing={listing.data} />
        )}
      </main>
      <footer className={styles.footer}>
        <p>
          Listed on <Link to="/">Rentbook</Link>, the shared rent book for landlords and tenants.
        </p>
      </footer>
    </div>
  )
}

function Listing({ listing }: { listing: PublicListing }) {
  const { place } = listing
  const available = listing.availableFrom <= todayIso() ? 'Now' : formatDate(listing.availableFrom)
  const [lead, ...rest] = listing.photos
  return (
    <div className={styles.layout}>
      <Sheet className={styles.sheet} aria-labelledby="offer-heading">
        <p className={styles.where}>
          {place.propertyName}, {place.city} {place.pincode}
        </p>
        <h1 id="offer-heading" className={styles.heading}>
          {offerName(place)}
        </h1>
        <p className={styles.rent}>
          <span className="entry num">{rupees(listing.rentPaise)}</span>
          <span className={styles.per}>a month</span>
        </p>
        <dl className={styles.terms}>
          <div>
            <dt>Deposit</dt>
            <dd className="entry num">{listing.depositPaise > 0 ? rupees(listing.depositPaise) : 'None'}</dd>
          </div>
          <div>
            <dt>Free from</dt>
            <dd className="entry">{available}</dd>
          </div>
          <div>
            <dt>Let by</dt>
            <dd className="entry">{listing.landlordFirstName}</dd>
          </div>
        </dl>
        {lead && (
          <figure className={styles.photos}>
            <img className={styles.lead} src={lead.url} alt={`${offerName(place)}, photo 1`} />
            {rest.length > 0 && (
              <div className={styles.more}>
                {rest.map((photo, index) => (
                  <img key={photo.id} src={photo.url} alt={`${offerName(place)}, photo ${index + 2}`} loading="lazy" />
                ))}
              </div>
            )}
          </figure>
        )}
        {listing.description && <p className={`${styles.description} entry`}>{listing.description}</p>}
      </Sheet>
      <Enquiry listing={listing} />
    </div>
  )
}

function Enquiry({ listing }: { listing: PublicListing }) {
  const landlord = listing.landlordFirstName
  const [form, setForm] = useState({ name: '', phone: '', email: '', visitOn: '', message: '', website: '' })
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [formError, setFormError] = useState<string>()
  const set = (key: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: event.target.value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(undefined)
    const digits = form.phone.replace(/\D/g, '')
    if (!form.name.trim()) return setFormError('Enter your name.')
    if (digits.length !== 10) return setFormError('Enter your 10-digit mobile number.')
    setBusy(true)
    try {
      await api(`/public/listings/${encodeURIComponent(listing.slug)}/enquiries`, {
        method: 'POST',
        json: {
          name: form.name.trim(),
          phone: `+91${digits}`,
          email: form.email.trim() || null,
          visitOn: form.visitOn || null,
          message: form.message.trim() || null,
          website: form.website,
        },
      })
      setSent(true)
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
    } finally {
      setBusy(false)
    }
  }

  if (sent) {
    return (
      <Sheet className={styles.sheet} aria-labelledby="sent-heading">
        <h2 id="sent-heading" className={styles.subheading}>
          Sent to {landlord}
        </h2>
        <p>
          {landlord} will call or message you on <span className="entry num">+91 {form.phone.replace(/\D/g, '')}</span>.
          The exact address comes with their reply.
        </p>
      </Sheet>
    )
  }

  return (
    <Sheet className={styles.sheet} aria-labelledby="ask-heading">
      <h2 id="ask-heading" className={styles.subheading}>
        Ask {landlord} about it
      </h2>
      <p className={styles.lede}>Leave your number; {landlord} replies with the address and a time to visit.</p>
      <form className={styles.form} onSubmit={submit} noValidate>
        <FormError>{formError}</FormError>
        <TextField label="Your name" autoComplete="name" value={form.name} onChange={set('name')} />
        <TextField
          label="Mobile number"
          prefix="+91"
          type="tel"
          inputMode="numeric"
          autoComplete="tel-national"
          value={form.phone}
          onChange={set('phone')}
        />
        <TextField label="Email (optional)" type="email" autoComplete="email" value={form.email} onChange={set('email')} />
        <TextField label="A day to visit (optional)" type="date" min={todayIso()} value={form.visitOn} onChange={set('visitOn')} />
        <TextAreaField label="Anything to ask (optional)" rows={3} maxLength={1000} value={form.message} onChange={set('message')} />
        {/* People never see this field; anything typed into it came from a bot. */}
        <div className={styles.trap} aria-hidden="true">
          <label>
            Website
            <input tabIndex={-1} autoComplete="off" value={form.website} onChange={set('website')} />
          </label>
        </div>
        <Button type="submit" block busy={busy}>
          {busy ? 'Sending' : `Send to ${landlord}`}
        </Button>
      </form>
    </Sheet>
  )
}
