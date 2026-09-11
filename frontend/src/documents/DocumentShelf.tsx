import { useState } from 'react'
import { ApiError } from '../api/client'
import type { VaultEntry } from '../api/types'
import { Button } from '../design/Button'
import { formatInstantDate, formatSize } from '../lib/format'
import styles from './Documents.module.css'
import { TYPE_LABELS } from './labels'
import { downloadDocument } from './queries'

/** A lease's shelf: each file as a ruled line with what it is, who filed it and when. */
export function DocumentShelf({ entries, label, onRemove }: {
  entries: VaultEntry[]
  label: string
  onRemove: (entry: VaultEntry) => Promise<unknown>
}) {
  if (entries.length === 0) {
    return <p className={styles.empty}>Nothing filed yet.</p>
  }
  return (
    <ul className={styles.shelf} aria-label={label}>
      {entries.map((entry) => (
        <ShelfLine key={entry.id} entry={entry} onRemove={onRemove} />
      ))}
    </ul>
  )
}

function ShelfLine({ entry, onRemove }: { entry: VaultEntry; onRemove: (entry: VaultEntry) => Promise<unknown> }) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  const act = async (work: () => Promise<unknown>) => {
    setBusy(true)
    setError(undefined)
    try {
      await work()
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'That did not go through. Try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className={styles.line}>
      <div className={styles.what}>
        <span className={styles.type}>
          {TYPE_LABELS[entry.type]}
          {entry.visibility === 'LANDLORD_ONLY' ? ', only you can see it' : ''}
        </span>
        <span className={`${styles.name} entry`}>{entry.filename}</span>
        <span className={styles.meta}>
          Filed by {entry.mine ? 'you' : entry.uploadedBy} on {formatInstantDate(entry.uploadedAt)}
          {entry.sizeBytes ? `, ${formatSize(entry.sizeBytes)}` : ''}
        </span>
      </div>
      <div className={styles.actions}>
        {!confirming && (
          <Button
            variant="quiet"
            className={styles.rowAction}
            aria-label={`Download ${entry.filename}`}
            onClick={() => void act(() => downloadDocument(entry.id))}
          >
            Download
          </Button>
        )}
        {entry.mine && !confirming && (
          <Button
            variant="quiet"
            className={styles.rowAction}
            aria-label={`Remove ${entry.filename}`}
            onClick={() => setConfirming(true)}
          >
            Remove
          </Button>
        )}
        {entry.mine && confirming && (
          <span className={styles.confirm}>
            <span>Take {entry.filename} off the shelf?</span>
            <Button variant="quiet" className={styles.rowAction} busy={busy} onClick={() => void act(() => onRemove(entry))}>
              Remove it
            </Button>
            <Button variant="quiet" className={styles.rowAction} onClick={() => setConfirming(false)}>
              Keep it
            </Button>
          </span>
        )}
        {error && (
          <span role="alert" className={styles.error}>
            {error}
          </span>
        )}
      </div>
    </li>
  )
}
