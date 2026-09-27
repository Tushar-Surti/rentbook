import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api, ApiError } from '../api/client'
import type { ListingEnquiry, ListingView, PhotoUploadTicket } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Button, LinkButton } from '../design/Button'
import { FormError, TextAreaField, TextField } from '../design/Field'
import { Icon } from '../design/Icon'
import { Mark } from '../design/Mark'
import { propertyQuery } from '../landlord/queries'
import { formatDate, formatInstantShort, rupees, todayIso, toPaise } from '../lib/format'
import { placeName } from './place'
import styles from './Listings.module.css'

const listingsQuery = { queryKey: ['listings'], queryFn: () => api<ListingView[]>('/listings') }
const listingQuery = (id: string) => ({ queryKey: ['listing', id], queryFn: () => api<ListingView>(`/listings/${id}`) })
const unreachable = 'Could not reach Rentbook. Check your connection.'

/** Every listing the landlord has made, open ones first, with how many enquiries wait. */
export function ListingsPage() {
  const listings = useQuery(listingsQuery)
  if (listings.isPending) return <SessionLoading />
  const all = [...(listings.data ?? [])].sort((a, b) => Number(b.status === 'OPEN') - Number(a.status === 'OPEN'))
  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Listings</h1>
        <p className={styles.lede}>
          A vacant flat, room or bed on a page of its own, to share on WhatsApp or anywhere. List one from a vacant row in
          your register.
        </p>
      </header>
      {all.length === 0 ? (
        <p className={styles.empty}>Nothing listed yet. Open a property and choose "List it" on a vacant row.</p>
      ) : (
        <ul className={styles.list}>
          {all.map((listing) => {
            const waiting = listing.enquiries.filter((enquiry) => enquiry.status === 'NEW').length
            return (
              <li key={listing.id}>
                <Link to={`/l/listings/${listing.id}`} className={styles.listTitle}>
                  {placeName(listing.place)}
                </Link>
                <span className="entry num">{rupees(listing.rentPaise)} a month</span>
                <span className={styles.listState}>
                  {listing.status === 'OPEN' ? (
                    waiting > 0 ? (
                      <Mark tone="invited">
                        {waiting} new {waiting === 1 ? 'enquiry' : 'enquiries'}
                      </Mark>
                    ) : (
                      'Listed'
                    )
                  ) : (
                    'Closed'
                  )}
                </span>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

/** Listing a vacant unit: the terms, then photos and the link on the listing's own page. */
export function NewListingPage() {
  const { propertyId = '', unitId = '' } = useParams()
  const property = useQuery(propertyQuery(propertyId))
  const unit = property.data?.units.find((candidate) => candidate.id === unitId)
  if (property.isPending) return <SessionLoading />
  if (!unit || !property.data) {
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>This unit isn't in your book</h1>
        <Link to="/l">Open your book</Link>
      </div>
    )
  }
  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <Link to={`/l/p/${propertyId}`} className={styles.back}>
          Back to {property.data.name}
        </Link>
        <h1 className={styles.title}>
          List {unit.label}, {property.data.name}
        </h1>
        <p className={styles.lede}>
          The listing gets a page anyone can open from a link. It shows {property.data.name}'s name and area, never the
          street address; you share that when you reply to someone.
        </p>
      </header>
      <TermsForm
        unitId={unitId}
        initial={{
          rent: unit.defaultRentPaise ? String(unit.defaultRentPaise / 100) : '',
          deposit: unit.defaultDepositPaise ? String(unit.defaultDepositPaise / 100) : '',
          availableFrom: todayIso(),
          description: '',
        }}
      />
    </div>
  )
}

type Terms = { rent: string; deposit: string; availableFrom: string; description: string }

function TermsForm({ unitId, listing, initial }: { unitId?: string; listing?: ListingView; initial: Terms }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [terms, setTerms] = useState(initial)
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState('')
  const [formError, setFormError] = useState<string>()
  const set = (key: keyof Terms) => (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setTerms((current) => ({ ...current, [key]: event.target.value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(undefined)
    setSaved('')
    const rentPaise = toPaise(terms.rent)
    const depositPaise = terms.deposit.trim() ? toPaise(terms.deposit) : 0
    if (!rentPaise) return setFormError('Enter the rent a month.')
    if (depositPaise === null) return setFormError('Enter the deposit, or leave it empty for none.')
    if (!terms.availableFrom) return setFormError('Choose when it is free from.')
    setBusy(true)
    try {
      const json = { rentPaise, depositPaise, availableFrom: terms.availableFrom, description: terms.description.trim() || null }
      if (listing) {
        await api<ListingView>(`/listings/${listing.id}`, { method: 'PUT', json })
        await queryClient.invalidateQueries({ queryKey: ['listing', listing.id] })
        setSaved('Saved. The page shows the new terms now.')
      } else {
        const created = await api<ListingView>(`/units/${unitId}/listing`, { method: 'POST', json })
        await queryClient.invalidateQueries({ queryKey: ['board'] })
        await queryClient.invalidateQueries({ queryKey: ['listings'] })
        navigate(`/l/listings/${created.id}`)
      }
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
        <TextField label="Rent a month" prefix="₹" inputMode="decimal" value={terms.rent} onChange={set('rent')} />
        <TextField label="Deposit" prefix="₹" inputMode="decimal" value={terms.deposit} onChange={set('deposit')} />
        <TextField label="Free from" type="date" value={terms.availableFrom} onChange={set('availableFrom')} />
      </div>
      <TextAreaField
        label="About the place (optional)"
        hint="What someone would ask on the phone: what's included, who it suits, what's nearby."
        rows={5}
        maxLength={2000}
        value={terms.description}
        onChange={set('description')}
      />
      <div className={styles.actions}>
        <Button type="submit" busy={busy}>
          {busy ? 'Saving' : listing ? 'Save changes' : 'List it'}
        </Button>
        <p role="status" className={styles.saved}>
          {saved}
        </p>
      </div>
    </form>
  )
}

/** One listing: its link to share, its photos and terms, and the people who asked about it. */
export function ListingPage() {
  const { listingId = '' } = useParams()
  const listing = useQuery(listingQuery(listingId))
  if (listing.isPending) return <SessionLoading />
  if (listing.isError) {
    return (
      <div className={styles.page}>
        <h1 className={styles.title}>This listing isn't yours</h1>
        <Link to="/l/listings">See your listings</Link>
      </div>
    )
  }
  const view = listing.data
  const open = view.status === 'OPEN'
  const newest = view.enquiries.filter((enquiry) => enquiry.status !== 'DISMISSED')
  const link = `${window.location.origin}/r/${view.slug}`

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <Link to={`/l/p/${view.place.propertyId}`} className={styles.back}>
          Back to {view.place.propertyName}
        </Link>
        <h1 className={styles.title}>{placeName(view.place)}</h1>
        <p className={styles.lede}>
          {open ? (
            <>
              Listed since {formatInstantShort(view.createdAt)}. It closes by itself when someone moves in.
            </>
          ) : (
            'Closed. The page now says the place has been let.'
          )}
        </p>
      </header>

      {open && <Share link={link} place={placeName(view.place)} />}

      <section className={styles.section} aria-labelledby="enquiries-heading">
        <h2 id="enquiries-heading" className={styles.sectionHeading}>
          Enquiries
        </h2>
        {newest.length === 0 ? (
          <p className={styles.empty}>No one has asked yet. Share the link to start.</p>
        ) : (
          <ul className={styles.enquiries}>
            {newest.map((enquiry) => (
              <EnquiryRow key={enquiry.id} enquiry={enquiry} listing={view} />
            ))}
          </ul>
        )}
      </section>

      {open && (
        <>
          <section className={styles.section} aria-labelledby="photos-heading">
            <h2 id="photos-heading" className={styles.sectionHeading}>
              Photos
            </h2>
            <Photos listing={view} />
          </section>
          <section className={styles.section} aria-labelledby="terms-heading">
            <h2 id="terms-heading" className={styles.sectionHeading}>
              Terms and description
            </h2>
            <TermsForm
              listing={view}
              initial={{
                rent: String(view.rentPaise / 100),
                deposit: view.depositPaise ? String(view.depositPaise / 100) : '',
                availableFrom: view.availableFrom,
                description: view.description ?? '',
              }}
            />
          </section>
          <CloseListing listing={view} />
        </>
      )}
    </div>
  )
}

function Share({ link, place }: { link: string; place: string }) {
  const [copied, setCopied] = useState(false)
  const message = `${place} is available to rent. Photos, rent and how to ask: ${link}`
  return (
    <section className={styles.share} aria-labelledby="share-heading">
      <h2 id="share-heading" className={styles.sectionHeading}>
        The link to share
      </h2>
      <p className={`${styles.link} entry`}>{link}</p>
      <div className={styles.actions}>
        <Button variant="secondary" onClick={() => void navigator.clipboard.writeText(link).then(() => setCopied(true))}>
          <Icon name={copied ? 'check' : 'copy'} />
          {copied ? 'Copied' : 'Copy link'}
        </Button>
        <a className={styles.textLink} href={`https://wa.me/?text=${encodeURIComponent(message)}`} target="_blank" rel="noreferrer">
          Share on WhatsApp
        </a>
        <a className={styles.textLink} href={link} target="_blank" rel="noreferrer">
          Open the page
        </a>
      </div>
    </section>
  )
}

function EnquiryRow({ enquiry, listing }: { enquiry: ListingEnquiry; listing: ListingView }) {
  const queryClient = useQueryClient()
  const [busy, setBusy] = useState(false)
  const mark = async (status: 'INVITED' | 'DISMISSED') => {
    setBusy(true)
    try {
      await api(`/listings/${listing.id}/enquiries/${enquiry.id}`, { method: 'PATCH', json: { status } })
      await queryClient.invalidateQueries({ queryKey: ['listing', listing.id] })
      await queryClient.invalidateQueries({ queryKey: ['listings'] })
    } finally {
      setBusy(false)
    }
  }
  const invite = new URLSearchParams({
    name: enquiry.name,
    phone: enquiry.phone,
    ...(enquiry.email ? { email: enquiry.email } : {}),
    rent: String(listing.rentPaise / 100),
    ...(listing.depositPaise ? { deposit: String(listing.depositPaise / 100) } : {}),
  })
  return (
    <li className={styles.enquiry}>
      <div className={styles.enquiryHead}>
        <span className={`${styles.enquiryName} entry`}>{enquiry.name}</span>
        {enquiry.status === 'INVITED' ? <Mark tone="invited">Invited</Mark> : <Mark tone="working">New</Mark>}
        <span className={styles.meta}>{formatInstantShort(enquiry.receivedAt)}</span>
      </div>
      <p className={styles.contact}>
        <a href={`tel:${enquiry.phone}`} className="entry">
          <Icon name="phone" size={18} />
          {enquiry.phone}
        </a>
        {enquiry.email && (
          <a href={`mailto:${enquiry.email}`} className="entry">
            <Icon name="mail" size={18} />
            {enquiry.email}
          </a>
        )}
      </p>
      {enquiry.visitOn && (
        <p className={styles.meta}>
          Would like to visit on <span className="entry">{formatDate(enquiry.visitOn)}</span>
        </p>
      )}
      {enquiry.message && <p className="entry">{enquiry.message}</p>}
      {listing.status === 'OPEN' && (
        <div className={styles.actions}>
          <LinkButton
            to={`/l/p/${listing.place.propertyId}/units/${listing.place.unitId}/invite?${invite.toString()}`}
            variant="secondary"
            onClick={() => void mark('INVITED')}
          >
            Invite {enquiry.name.split(' ')[0]}
          </LinkButton>
          {enquiry.status === 'NEW' && (
            <Button variant="quiet" busy={busy} onClick={() => void mark('DISMISSED')}>
              Dismiss
            </Button>
          )}
        </div>
      )}
    </li>
  )
}

function Photos({ listing }: { listing: ListingView }) {
  const queryClient = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['listing', listing.id] })

  const add = async (files: FileList | null) => {
    if (!files || files.length === 0) return
    setBusy(true)
    setError(undefined)
    try {
      for (const file of Array.from(files)) {
        const ticket = await api<PhotoUploadTicket>(`/listings/${listing.id}/photos`, {
          method: 'POST',
          json: { contentType: file.type, sizeBytes: file.size },
        })
        const put = await fetch(ticket.url, { method: 'PUT', headers: ticket.headers, body: file })
        if (!put.ok) throw new Error('upload')
        await api(`/listings/${listing.id}/photos/${ticket.photoId}/complete`, { method: 'POST' })
      }
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : "A photo didn't upload. Check your connection and try again.")
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
      await refresh()
    }
  }

  const remove = async (photoId: string) => {
    await api(`/listings/${listing.id}/photos/${photoId}`, { method: 'DELETE' })
    await refresh()
  }

  return (
    <div className={styles.photos}>
      {listing.photos.length > 0 && (
        <ul className={styles.prints}>
          {listing.photos.map((photo, index) => (
            <li key={photo.id} className={styles.print}>
              <img src={photo.url} alt={`Photo ${index + 1} of ${placeName(listing.place)}`} />
              <Button variant="quiet" onClick={() => void remove(photo.id)}>
                Remove
              </Button>
            </li>
          ))}
        </ul>
      )}
      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
      {listing.photos.length < 8 && (
        <div className={styles.actions}>
          <input
            ref={input}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            className="visually-hidden"
            id="listing-photos"
            onChange={(event) => void add(event.target.files)}
          />
          <Button variant="secondary" busy={busy} onClick={() => input.current?.click()}>
            <Icon name="plus" />
            {busy ? 'Adding photos' : 'Add photos'}
          </Button>
          <p className={styles.meta}>Up to 8 photos, 10 MB each. The first one leads the page.</p>
        </div>
      )}
    </div>
  )
}

function CloseListing({ listing }: { listing: ListingView }) {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const close = async () => {
    setBusy(true)
    try {
      await api(`/listings/${listing.id}/close`, { method: 'POST' })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['listing', listing.id] }),
        queryClient.invalidateQueries({ queryKey: ['listings'] }),
        queryClient.invalidateQueries({ queryKey: ['board'] }),
      ])
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className={styles.section} aria-labelledby="close-heading">
      <h2 id="close-heading" className={styles.sectionHeading}>
        Close the listing
      </h2>
      {!confirming ? (
        <div>
          <Button variant="secondary" onClick={() => setConfirming(true)}>
            Close it
          </Button>
        </div>
      ) : (
        <div className={styles.actions}>
          <p className={styles.question}>Close it? The page will say the place has been let.</p>
          <Button busy={busy} onClick={() => void close()}>
            Close the listing
          </Button>
          <Button variant="secondary" onClick={() => setConfirming(false)}>
            Keep it open
          </Button>
        </div>
      )}
    </section>
  )
}
