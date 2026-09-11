import { useQueryClient } from '@tanstack/react-query'
import { useId, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import type { DocumentType, Role } from '../api/types'
import { Button } from '../design/Button'
import buttonStyles from '../design/Button.module.css'
import { FormError, SelectField } from '../design/Field'
import { uploadFile } from '../lib/upload'
import styles from './Documents.module.css'
import { ACCEPTED_FILES, MAX_FILE_BYTES } from './labels'

const OPTIONS: Record<Role, { value: DocumentType; label: string }[]> = {
  LANDLORD: [
    { value: 'LEASE', label: 'Lease agreement' },
    { value: 'OTHER', label: 'Something else' },
  ],
  TENANT: [
    { value: 'KYC', label: 'ID document (Aadhaar, PAN or passport)' },
    { value: 'OTHER', label: 'Something else' },
  ],
}

/**
 * Files a document on the lease: straight to storage, confirmed there, then on both shelves. Only a
 * landlord can keep a file to themselves. {@code prominent} makes Filing the page's one primary action.
 */
export function FileDocument({ leaseId, viewer, prominent }: { leaseId: string; viewer: Role; prominent: boolean }) {
  const queryClient = useQueryClient()
  const inputId = useId()
  const options = OPTIONS[viewer]
  const [type, setType] = useState<DocumentType>(options[0].value)
  const [privateToMe, setPrivateToMe] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [filed, setFiled] = useState('')

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!file) {
      setError('Choose a file first.')
      return
    }
    if (!ACCEPTED_FILES.includes(file.type)) {
      setError('File a PDF or a photo (JPEG, PNG or WebP).')
      return
    }
    if (file.size > MAX_FILE_BYTES) {
      setError('Files can be up to 10 MB.')
      return
    }
    setBusy(true)
    setError(undefined)
    setFiled('')
    try {
      const keepPrivate = viewer === 'LANDLORD' && type === 'OTHER' && privateToMe
      await uploadFile(file, leaseId, type, keepPrivate ? 'LANDLORD_ONLY' : undefined)
      setFiled(`Filed ${file.name}.`)
      setFile(null)
      await queryClient.invalidateQueries({ queryKey: ['vault', leaseId] })
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : "Couldn't file that. Try again.")
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={(event) => void submit(event)} noValidate>
      <FormError>{error}</FormError>
      <div className={styles.pair}>
        <SelectField
          label="What it is"
          options={options}
          value={type}
          onChange={(event) => setType(event.target.value as DocumentType)}
        />
        <div className={styles.chooser}>
          <label htmlFor={inputId} className={`${buttonStyles.button} ${buttonStyles.secondary} ${styles.pick}`}>
            Choose a file
            <input
              id={inputId}
              type="file"
              accept={ACCEPTED_FILES.join(',')}
              className="visually-hidden"
              onChange={(event) => {
                setFile(event.target.files?.[0] ?? null)
                setFiled('')
                event.target.value = ''
              }}
            />
          </label>
          <span className={file ? `${styles.chosen} entry` : styles.hint}>
            {file ? file.name : 'A PDF or a photo, up to 10 MB.'}
          </span>
        </div>
      </div>
      {viewer === 'LANDLORD' && type === 'OTHER' && (
        <label className={styles.check}>
          <input type="checkbox" checked={privateToMe} onChange={(event) => setPrivateToMe(event.target.checked)} />
          Only I can see this
        </label>
      )}
      <div className={styles.submit}>
        <Button type="submit" variant={prominent ? 'primary' : 'secondary'} busy={busy}>
          {busy ? 'Filing' : 'File it'}
        </Button>
        <p role="status" className={styles.filed}>
          {filed}
        </p>
      </div>
    </form>
  )
}
