import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api, ApiError } from '../api/client'
import type { Condition, ConditionLine, ConditionReport } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Button } from '../design/Button'
import buttonStyles from '../design/Button.module.css'
import { FormError, SelectField, TextAreaField, TextField } from '../design/Field'
import { Icon } from '../design/Icon'
import { useLeaseLive } from '../ledger/useLedger'
import { formatInstantDate } from '../lib/format'
import { uploadConditionPhoto } from '../lib/upload'
import styles from './ConditionReport.module.css'
import { CONDITIONS, conditionLabel, conditionReportQuery, reportName } from './queries'

type Viewer = 'LANDLORD' | 'TENANT'

const unreachable = 'Could not reach Rentbook. Check your connection.'
const MAX_PHOTOS = 6
const PHOTO_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])

export function LandlordConditionPage() {
  const { propertyId = '', leaseId = '', reportId = '' } = useParams()
  return <ReportPage reportId={reportId} viewer="LANDLORD" back={`/l/p/${propertyId}/leases/${leaseId}`} />
}

export function TenantConditionPage() {
  const { reportId = '' } = useParams()
  return <ReportPage reportId={reportId} viewer="TENANT" back="/t" />
}

const firstName = (fullName: string) => fullName.split(' ')[0]

/**
 * One condition report, read by both parties. The landlord writes it line by line while it's a draft; the
 * tenant reads the same lines, adds notes and photos where they see things differently, and confirms it.
 */
function ReportPage({ reportId, viewer, back }: { reportId: string; viewer: Viewer; back: string }) {
  const queryClient = useQueryClient()
  const report = useQuery(conditionReportQuery(reportId))
  useLeaseLive(report.data?.lease.id ?? null)
  const [notes, setNotes] = useState<Record<string, string>>({})

  if (report.isPending) return <SessionLoading />
  if (report.isError) {
    return (
      <div className={styles.missing}>
        <h1 className={styles.title}>This report isn't here</h1>
        <Link to={back}>{viewer === 'LANDLORD' ? 'Back to the lease' : 'Back to your home'}</Link>
      </div>
    )
  }

  const view = report.data
  const tenant = firstName(view.lease.tenant.fullName)
  const landlord = firstName(view.lease.landlord.fullName)
  const editing = viewer === 'LANDLORD' && view.status === 'DRAFT'
  const reading = viewer === 'TENANT' && view.status === 'SENT'
  const unit = view.lease.unit.roomLabel ? `${view.lease.unit.label}, ${view.lease.unit.roomLabel}` : view.lease.unit.label
  const areas = [...new Set(view.lines.map((line) => line.area))]
  const replace = (next: ConditionReport) => queryClient.setQueryData(['condition', reportId], next)
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['condition', reportId] })

  const status =
    view.status === 'DRAFT'
      ? `Draft. ${tenant} sees it once you send it.`
      : view.status === 'SENT'
        ? viewer === 'LANDLORD'
          ? `Sent to ${tenant} on ${formatInstantDate(view.sentAt!)}. Waiting for them to confirm it.`
          : `${landlord} sent this on ${formatInstantDate(view.sentAt!)}.`
        : viewer === 'LANDLORD'
          ? `Confirmed by ${tenant} on ${formatInstantDate(view.confirmedAt!)}.`
          : `You confirmed this on ${formatInstantDate(view.confirmedAt!)}.`

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <Link to={back} className={styles.back}>
          {viewer === 'LANDLORD' ? `${view.lease.tenant.fullName}'s lease` : 'Your home'}
        </Link>
        <h1 className={styles.title}>{reportName(view.kind)}</h1>
        <p className={styles.where}>
          {unit}, {view.lease.property.name}
        </p>
        <p className={styles.status} role="status">
          {status}
        </p>
        {view.comparedWithMoveIn && (
          <p className={view.worse > 0 ? styles.worseSummary : styles.status}>
            {view.worse === 0
              ? 'Nothing is worse than at move-in.'
              : `${view.worse} ${view.worse === 1 ? 'line is' : 'lines are'} worse than at move-in.`}
          </p>
        )}
        {reading && (
          <p className={styles.guide}>
            Read each line. Where you see something differently, add a note or a photo to it. Then confirm the report
            at the end.
          </p>
        )}
      </header>

      {areas.map((area) => (
        <section key={area} className={styles.area} aria-labelledby={`area-${area}`}>
          <h2 id={`area-${area}`} className={styles.areaHeading}>
            {area}
          </h2>
          <ul className={styles.lines}>
            {view.lines
              .filter((line) => line.area === area)
              .map((line) => (
                <LineRow
                  key={line.id}
                  line={line}
                  report={view}
                  editing={editing}
                  reading={reading}
                  tenant={tenant}
                  note={notes[line.id] ?? ''}
                  onNote={(note) => setNotes((current) => ({ ...current, [line.id]: note }))}
                  onReport={replace}
                  onPhotos={refresh}
                />
              ))}
          </ul>
        </section>
      ))}

      {editing && <AddLine report={view} areas={areas} onReport={replace} />}
      {editing && <SendDraft report={view} tenant={tenant} back={back} onReport={replace} />}
      {reading && <ConfirmReport report={view} landlord={landlord} notes={notes} onReport={replace} />}
    </div>
  )
}

function LineRow({ line, report, editing, reading, tenant, note, onNote, onReport, onPhotos }: {
  line: ConditionLine
  report: ConditionReport
  editing: boolean
  reading: boolean
  tenant: string
  note: string
  onNote: (note: string) => void
  onReport: (report: ConditionReport) => void
  onPhotos: () => Promise<unknown>
}) {
  const queryClient = useQueryClient()
  const [savedNote, setSavedNote] = useState(line.note ?? '')
  const [draftNote, setDraftNote] = useState(line.note ?? '')
  const [saving, setSaving] = useState<'idle' | 'saving' | 'saved' | 'failed'>('idle')
  const [removing, setRemoving] = useState(false)
  const [busy, setBusy] = useState(false)
  // A tenant's note box opens only on the lines they want to say something about.
  const [noting, setNoting] = useState(false)

  const save = async (condition: Condition, text: string) => {
    setSaving('saving')
    try {
      const updated = await api<ConditionLine>(`/condition-lines/${line.id}`, {
        method: 'PATCH',
        json: { condition, note: text.trim() || null },
      })
      setSavedNote(text)
      queryClient.setQueryData<ConditionReport>(['condition', report.id], (current) => {
        if (!current) return current
        const lines = current.lines.map((found) => (found.id === updated.id ? updated : found))
        return { ...current, lines, worse: lines.filter((found) => found.worse).length }
      })
      setSaving('saved')
    } catch {
      setSaving('failed')
    }
  }

  const remove = async () => {
    setBusy(true)
    try {
      onReport(await api<ConditionReport>(`/condition-lines/${line.id}`, { method: 'DELETE' }))
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className={styles.line} data-editing={editing || undefined}>
      <div className={styles.lineHead}>
        <h3 className={styles.item}>{line.item}</h3>
        {report.comparedWithMoveIn && (
          <span className={styles.before}>
            {line.atMoveIn ? `At move-in: ${conditionLabel(line.atMoveIn)}` : 'Not on the move-in report'}
          </span>
        )}
        {line.worse && <span className={styles.worse}>Worse</span>}
        {editing && (
          <span className={saving === 'failed' ? styles.error : styles.saved} role="status">
            {saving === 'saving' ? 'Saving' : saving === 'saved' ? 'Saved' : saving === 'failed' ? "Didn't save. Try again." : ''}
          </span>
        )}
      </div>

      {editing ? (
        <div className={styles.edit}>
          <SelectField
            label={`Condition of ${line.item}`}
            className={styles.bare}
            value={line.condition}
            options={CONDITIONS}
            onChange={(event) => void save(event.target.value as Condition, draftNote)}
          />
          <TextField
            label={`Note on ${line.item}`}
            className={styles.bare}
            placeholder="Note, if any"
            maxLength={500}
            value={draftNote}
            onChange={(event) => setDraftNote(event.target.value)}
            onBlur={() => {
              if (draftNote.trim() !== savedNote.trim()) void save(line.condition, draftNote)
            }}
          />
        </div>
      ) : (
        <p className={styles.found}>
          <span className={`entry ${styles.condition}`}>{conditionLabel(line.condition)}</span>
          {line.note && <span className="entry">{line.note}</span>}
        </p>
      )}

      {(editing || reading || line.photos.length > 0) && (
        <div className={styles.tools}>
          <Photos line={line} canAdd={editing || reading} onChanged={onPhotos} />
          {reading && !noting && !note && (
            <Button variant="quiet" className={styles.small} onClick={() => setNoting(true)}>
              Add a note
            </Button>
          )}
          {editing && (
            <div className={styles.lineActions}>
              {!removing ? (
                <Button variant="quiet" className={styles.small} onClick={() => setRemoving(true)}>
                  Remove line
                </Button>
              ) : (
                <>
                  <span className={styles.question}>
                    Remove {line.item}
                    {line.photos.length > 0 ? ' and its photos' : ''}?
                  </span>
                  <Button variant="quiet" className={styles.small} busy={busy} onClick={() => void remove()}>
                    Remove it
                  </Button>
                  <Button variant="quiet" className={styles.small} onClick={() => setRemoving(false)}>
                    Keep it
                  </Button>
                </>
              )}
            </div>
          )}
        </div>
      )}

      {reading ? (
        (noting || note) && (
          <TextAreaField
            label={`Your note on ${line.item}`}
            className={styles.tenantField}
            rows={2}
            maxLength={500}
            autoFocus={noting && !note}
            value={note}
            onChange={(event) => onNote(event.target.value)}
          />
        )
      ) : (
        line.tenantNote && (
          <p className={styles.tenantNote}>
            <span className={styles.noteBy}>{tenant}'s note</span>
            <span className="entry">{line.tenantNote}</span>
          </p>
        )
      )}
    </li>
  )
}

/** The line's photos as prints; whoever may add photos right now can add theirs and take their own off. */
function Photos({ line, canAdd, onChanged }: { line: ConditionLine; canAdd: boolean; onChanged: () => Promise<unknown> }) {
  const inputId = useId()
  const [uploading, setUploading] = useState(0)
  const [error, setError] = useState<string>()

  const add = async (files: FileList | null) => {
    if (!files) return
    setError(undefined)
    const picked = Array.from(files).slice(0, Math.max(0, MAX_PHOTOS - line.photos.length))
    const bad = picked.find((file) => !PHOTO_TYPES.has(file.type) || file.size > 10 * 1024 * 1024)
    if (bad) {
      setError(PHOTO_TYPES.has(bad.type) ? 'Photos can be up to 10 MB.' : 'Use a JPEG, PNG or WebP photo.')
      return
    }
    setUploading((count) => count + picked.length)
    await Promise.all(
      picked.map((file) =>
        uploadConditionPhoto(file, line.id)
          .catch((failure) => setError(failure instanceof ApiError ? failure.message : "Couldn't upload that photo."))
          .finally(() => setUploading((count) => count - 1)),
      ),
    )
    await onChanged()
  }

  const remove = async (photoId: string) => {
    setError(undefined)
    try {
      await api(`/condition-photos/${photoId}`, { method: 'DELETE' })
      await onChanged()
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : unreachable)
    }
  }

  if (!canAdd && line.photos.length === 0) return null
  return (
    <div className={styles.photos}>
      {line.photos.length > 0 && (
        <ul className={styles.prints} aria-label={`Photos of ${line.item}`}>
          {line.photos.map((photo, index) => (
            <li key={photo.id} className={styles.print}>
              <a href={photo.url} target="_blank" rel="noreferrer">
                <img src={photo.url} alt={`${line.item}, photo ${index + 1}`} loading="lazy" />
              </a>
              {photo.byTenant && <span className={styles.by}>Tenant's photo</span>}
              {canAdd && photo.mine && (
                <Button variant="quiet" className={styles.small} onClick={() => void remove(photo.id)}>
                  Remove
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {canAdd && line.photos.length + uploading < MAX_PHOTOS && (
        <label htmlFor={inputId} className={`${buttonStyles.button} ${buttonStyles.quiet} ${styles.addPhoto}`}>
          <Icon name="plus" size={18} />
          {uploading > 0 ? 'Uploading' : 'Add photo'}
          <input
            id={inputId}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            className="visually-hidden"
            onChange={(event) => {
              void add(event.target.files)
              event.target.value = ''
            }}
          />
        </label>
      )}
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

const ELSEWHERE = '__elsewhere'

function AddLine({ report, areas, onReport }: {
  report: ConditionReport
  areas: string[]
  onReport: (report: ConditionReport) => void
}) {
  const [area, setArea] = useState(areas[0] ?? ELSEWHERE)
  const [newArea, setNewArea] = useState('')
  const [item, setItem] = useState('')
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string>()

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(undefined)
    const where = area === ELSEWHERE ? newArea.trim() : area
    if (!where) return setFormError('Say which room or area it is in.')
    if (!item.trim()) return setFormError('Say what it is, like "Fridge" or "Curtains".')
    setBusy(true)
    try {
      onReport(
        await api<ConditionReport>(`/condition-reports/${report.id}/lines`, {
          method: 'POST',
          json: { area: where, item: item.trim() },
        }),
      )
      setItem('')
      setNewArea('')
      if (area === ELSEWHERE) setArea(where)
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : unreachable)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className={styles.add} aria-labelledby="add-line-heading">
      <h2 id="add-line-heading" className={styles.areaHeading}>
        Add a line
      </h2>
      <form className={styles.addForm} onSubmit={submit} noValidate>
        <FormError>{formError}</FormError>
        <div className={styles.addFields}>
          <SelectField
            label="Where"
            value={area}
            onChange={(event) => setArea(event.target.value)}
            options={[...areas.map((name) => ({ value: name, label: name })), { value: ELSEWHERE, label: 'Somewhere else' }]}
          />
          {area === ELSEWHERE && (
            <TextField label="Room or area" placeholder="Study" maxLength={60} value={newArea} onChange={(event) => setNewArea(event.target.value)} />
          )}
          <TextField label="What" placeholder="Fridge" maxLength={80} value={item} onChange={(event) => setItem(event.target.value)} />
        </div>
        <div>
          <Button type="submit" variant="secondary" busy={busy}>
            {busy ? 'Adding' : 'Add line'}
          </Button>
        </div>
      </form>
    </section>
  )
}

function SendDraft({ report, tenant, back, onReport }: {
  report: ConditionReport
  tenant: string
  back: string
  onReport: (report: ConditionReport) => void
}) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [busy, setBusy] = useState<'send' | 'discard'>()
  const [discarding, setDiscarding] = useState(false)
  const [formError, setFormError] = useState<string>()

  const act = async (what: 'send' | 'discard') => {
    setBusy(what)
    setFormError(undefined)
    try {
      if (what === 'send') {
        onReport(await api<ConditionReport>(`/condition-reports/${report.id}/send`, { method: 'POST' }))
      } else {
        await api(`/condition-reports/${report.id}`, { method: 'DELETE' })
        navigate(back)
      }
      await queryClient.invalidateQueries({ queryKey: ['conditions', report.lease.id] })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : unreachable)
    } finally {
      setBusy(undefined)
    }
  }

  return (
    <section className={styles.finish} aria-labelledby="send-heading">
      <h2 id="send-heading" className={styles.areaHeading}>
        Send it to {tenant}
      </h2>
      <p className={styles.guide}>
        {tenant} reads this same report, can add a note or photo to any line, and confirms it. Once sent, it stays as it
        is.
      </p>
      <FormError>{formError}</FormError>
      <div className={styles.finishActions}>
        <Button busy={busy === 'send'} onClick={() => void act('send')}>
          {busy === 'send' ? 'Sending' : `Send to ${tenant}`}
        </Button>
        {!discarding ? (
          <Button variant="quiet" onClick={() => setDiscarding(true)}>
            Discard draft
          </Button>
        ) : (
          <>
            <span className={styles.question}>Discard this draft and its photos?</span>
            <Button variant="quiet" busy={busy === 'discard'} onClick={() => void act('discard')}>
              Discard it
            </Button>
            <Button variant="quiet" onClick={() => setDiscarding(false)}>
              Keep it
            </Button>
          </>
        )}
      </div>
    </section>
  )
}

function ConfirmReport({ report, landlord, notes, onReport }: {
  report: ConditionReport
  landlord: string
  notes: Record<string, string>
  onReport: (report: ConditionReport) => void
}) {
  const queryClient = useQueryClient()
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string>()
  const written = Object.entries(notes).filter(([, note]) => note.trim())

  const confirm = async () => {
    setBusy(true)
    setFormError(undefined)
    try {
      onReport(
        await api<ConditionReport>(`/condition-reports/${report.id}/confirm`, {
          method: 'POST',
          json: { notes: written.map(([lineId, note]) => ({ lineId, note: note.trim() })) },
        }),
      )
      await queryClient.invalidateQueries({ queryKey: ['conditions', report.lease.id] })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : unreachable)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className={styles.finish} aria-labelledby="confirm-heading">
      <h2 id="confirm-heading" className={styles.areaHeading}>
        Confirm the report
      </h2>
      <p className={styles.guide}>
        {written.length === 0
          ? `Confirming says this is how the home was. ${landlord} is told, and the report stays as it is.`
          : `Your ${written.length === 1 ? 'note goes' : `${written.length} notes go`} with it. ${landlord} is told, and the report stays as it is.`}
      </p>
      <FormError>{formError}</FormError>
      <div className={styles.finishActions}>
        <Button busy={busy} onClick={() => void confirm()}>
          {busy ? 'Confirming' : 'Confirm report'}
        </Button>
      </div>
    </section>
  )
}
