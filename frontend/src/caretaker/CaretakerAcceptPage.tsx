import { useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api, ApiError } from '../api/client'
import type { CaretakerPreview } from '../api/types'
import { SessionLoading } from '../app/guards'
import { AuthLayout } from '../auth/AuthLayout'
import authStyles from '../auth/AuthLayout.module.css'
import { useAuth } from '../auth/AuthProvider'
import { Button } from '../design/Button'
import { FormError, TextField } from '../design/Field'

/** Where an invited caretaker lands from the email: see whose properties, set a password, start. */
export function CaretakerAcceptPage() {
  const { token = '' } = useParams()
  const { acceptCaretakerInvite } = useAuth()
  const navigate = useNavigate()
  const preview = useQuery({
    queryKey: ['caretaker-invite', token],
    queryFn: () => api<CaretakerPreview>(`/caretaker-invites/${encodeURIComponent(token)}`),
    retry: false,
  })
  const [name, setName] = useState<string>()
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string>()
  const [passwordError, setPasswordError] = useState<string>()

  if (preview.isPending) return <SessionLoading />
  if (preview.isError) {
    const gone = preview.error instanceof ApiError && preview.error.status === 410
    return (
      <AuthLayout
        heading={gone ? 'This invite has expired' : "This invite couldn't be opened"}
        lede={gone ? 'Ask the landlord to send you a new link.' : 'Check your connection and open the link again.'}
        below={
          <p>
            Already joined? <Link to="/login">Sign in</Link>
          </p>
        }
      >
        {null}
      </AuthLayout>
    )
  }

  const invite = preview.data
  const places = invite.properties.map((property) => property.name).join(', ')

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(undefined)
    setPasswordError(undefined)
    if (password.length < 8) {
      setPasswordError('Use at least 8 characters.')
      return
    }
    setBusy(true)
    try {
      await acceptCaretakerInvite(token, { fullName: (name ?? invite.fullName).trim(), password })
      navigate('/c', { replace: true })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
      setBusy(false)
    }
  }

  return (
    <AuthLayout
      heading={`Look after ${places}`}
      lede={`${invite.landlordName} has asked you to be the caretaker: see who has paid, record rent paid to you in cash, and handle repair requests.`}
      below={
        <p>
          Already joined? <Link to="/login">Sign in</Link>
        </p>
      }
    >
      <form className={authStyles.form} onSubmit={submit} noValidate>
        <FormError>{formError}</FormError>
        <TextField label="Email" value={invite.email} readOnly hint="Your landlord invited this address." />
        <TextField
          label="Your name"
          autoComplete="name"
          value={name ?? invite.fullName}
          onChange={(event) => setName(event.target.value)}
        />
        <TextField
          label="Choose a password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters."
          error={passwordError}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        <div className={authStyles.actions}>
          <Button type="submit" block busy={busy}>
            {busy ? 'Joining' : 'Join as caretaker'}
          </Button>
        </div>
      </form>
    </AuthLayout>
  )
}
