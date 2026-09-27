import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { api, ApiError } from '../api/client'
import type { Role, TicketEvent, TicketStatus, TicketThread } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Button } from '../design/Button'
import { FormError, TextAreaField } from '../design/Field'
import { Mark } from '../design/Mark'
import { formatInstantDate, formatMoment } from '../lib/format'
import { useLive } from '../realtime/LiveProvider'
import { CATEGORY_LABELS, firstName, MOVE_LABELS, statusSentence } from './labels'
import { PhotoPicker } from './PhotoPicker'
import { threadQuery } from './queries'
import { TicketStatusMark } from './TicketMarks'
import styles from './Thread.module.css'
import { usePhotoUploads } from './usePhotoUploads'

/**
 * One request's thread: a single ruled page both people write on, oldest first. Status changes are
 * written into it as lines of their own, photos are pinned in as prints, and it updates live for both.
 */
/** {@code base} is "/caretaker" when a caretaker reads it; they write as the landlord's side, without photos. */
export function Thread({ ticketId, viewer, base = '' }: { ticketId: string; viewer: Role; base?: string }) {
  const queryClient = useQueryClient()
  const thread = useQuery(threadQuery(ticketId, base))
  useLive(`/topic/tickets/${ticketId}`, () => {
    void queryClient.invalidateQueries({ queryKey: ['ticket', ticketId] })
    void queryClient.invalidateQueries({ queryKey: ['tickets'] })
  })

  if (thread.isPending) return <SessionLoading />
  if (thread.isError) {
    return (
      <div className={styles.missing}>
        <h1 className={styles.title}>This request isn't in your book</h1>
        <Link to={viewer === 'TENANT' ? '/t/requests' : viewer === 'CARETAKER' ? '/c' : '/l'}>
          {viewer === 'TENANT' ? 'See your requests' : 'Open the book'}
        </Link>
      </div>
    )
  }

  const { ticket, events, nextStatuses } = thread.data
  const unit = ticket.unit.roomLabel ? `${ticket.unit.label}, ${ticket.unit.roomLabel}` : ticket.unit.label
  const other = viewer !== 'TENANT' ? ticket.tenant : ticket.landlord

  return (
    <article className={styles.thread} aria-labelledby="request-title">
      <header className={styles.header}>
        <h1 id="request-title" className={styles.title}>
          {ticket.title}
        </h1>
        <p className={styles.where}>
          {unit}, {ticket.property.name}.{' '}
          {viewer !== 'TENANT' ? `Reported by ${ticket.tenant.fullName}` : 'Reported'} on{' '}
          {formatInstantDate(ticket.openedAt)}.
        </p>
        <p className={styles.marks}>
          <TicketStatusMark status={ticket.status} />
          {ticket.priority === 'URGENT' && <Mark tone="urgent">Urgent</Mark>}
          <span className={styles.category}>{CATEGORY_LABELS[ticket.category]}</span>
        </p>
      </header>

      <ol className={styles.lines} aria-label={`Thread with ${other.fullName}`}>
        {events.map((event) => (
          <Line key={event.id} event={event} />
        ))}
      </ol>

      {nextStatuses.length > 0 && <StatusActions ticketId={ticketId} nextStatuses={nextStatuses} base={base} />}

      {ticket.status === 'CLOSED' ? (
        <p className={styles.closed}>
          {viewer === 'TENANT'
            ? 'This request is closed. Reopen it to add to it.'
            : `This request is closed. ${firstName(ticket.tenant.fullName)} can reopen it.`}
        </p>
      ) : (
        <Reply ticketId={ticketId} leaseId={ticket.leaseId} base={base} photos={viewer !== 'CARETAKER'} />
      )}
    </article>
  )
}

function Line({ event }: { event: TicketEvent }) {
  if (event.kind === 'STATUS_CHANGE' && event.toStatus) {
    return (
      <li className={styles.statusLine}>
        <span>{statusSentence(event.author.name, event.toStatus)}</span>
        <time dateTime={event.at} className={styles.time}>
          {formatMoment(event.at)}
        </time>
      </li>
    )
  }
  return (
    <li className={styles.message}>
      <p className={styles.byline}>
        <span className={styles.author}>{event.author.name}</span>
        <time dateTime={event.at} className={styles.time}>
          {formatMoment(event.at)}
        </time>
      </p>
      {event.body && <p className={`${styles.body} entry`}>{event.body}</p>}
      {event.photos.length > 0 && (
        <ul className={styles.prints} aria-label="Photos">
          {event.photos.map((photo) => (
            <li key={photo.id}>
              <a href={photo.url} target="_blank" rel="noreferrer" aria-label={`Open photo ${photo.filename}`}>
                <img src={photo.url} alt={`Photo from ${event.author.name}`} loading="lazy" />
              </a>
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}

function StatusActions({ ticketId, nextStatuses, base }: { ticketId: string; nextStatuses: TicketStatus[]; base: string }) {
  const queryClient = useQueryClient()
  const move = useMutation({
    mutationFn: (status: TicketStatus) =>
      api<TicketThread>(`${base}/tickets/${ticketId}/status`, { method: 'PATCH', json: { status } }),
    onSuccess: (thread) => {
      queryClient.setQueryData(['ticket', ticketId], thread)
      void queryClient.invalidateQueries({ queryKey: ['tickets'] })
    },
  })
  return (
    <div className={styles.actions} role="group" aria-label="Move this request on">
      {nextStatuses.map((status) => (
        // Writing is the thread's one primary action, so moving the request on is always secondary.
        <Button
          key={status}
          variant="secondary"
          busy={move.isPending && move.variables === status}
          onClick={() => move.mutate(status)}
        >
          {MOVE_LABELS[status]}
        </Button>
      ))}
      {move.error && (
        <p role="alert" className={styles.error}>
          {move.error instanceof ApiError ? move.error.message : 'That did not go through. Try again.'}
        </p>
      )}
    </div>
  )
}

function Reply({ ticketId, leaseId, base, photos: withPhotos }: {
  ticketId: string
  leaseId: string
  base: string
  photos: boolean
}) {
  const queryClient = useQueryClient()
  const photos = usePhotoUploads(leaseId)
  const [body, setBody] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string>()

  const send = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (photos.busy) {
      setError('Wait for the photos to finish uploading.')
      return
    }
    if (!body.trim() && photos.ids.length === 0) {
      setError('Write something or attach a photo.')
      return
    }
    setSending(true)
    setError(undefined)
    try {
      await api(`${base}/tickets/${ticketId}/events`, {
        method: 'POST',
        json: withPhotos ? { body, photoIds: photos.ids } : { body },
      })
      setBody('')
      photos.clear()
      await queryClient.invalidateQueries({ queryKey: ['ticket', ticketId] })
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'That did not go through. Try again.')
    } finally {
      setSending(false)
    }
  }

  return (
    <form className={styles.reply} onSubmit={(event) => void send(event)} noValidate>
      <FormError>{error}</FormError>
      <TextAreaField label="Add to the thread" rows={3} value={body} onChange={(event) => setBody(event.target.value)} />
      {withPhotos && <PhotoPicker photos={photos} />}
      <div>
        <Button type="submit" busy={sending}>
          Send
        </Button>
      </div>
    </form>
  )
}
