import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../api/client'
import { Button } from '../design/Button'
import { FormError, TextField } from '../design/Field'
import { AuthLayout } from './AuthLayout'
import authStyles from './AuthLayout.module.css'
import { useAuth } from './AuthProvider'

const emailSchema = z.object({
  email: z.email('Enter the email you signed up with'),
})

const resetSchema = z.object({
  code: z.string().trim().regex(/^[0-9]{6}$/, 'Enter the 6-digit code from the email'),
  password: z.string().min(8, 'Use at least 8 characters').max(72, 'Use 72 characters or fewer'),
})

type EmailValues = z.infer<typeof emailSchema>
type ResetValues = z.infer<typeof resetSchema>

const unreachable = 'Could not reach Rentbook. Check your connection.'

/** Two steps: the email, then the emailed code with a new password. A successful reset signs you in. */
export function ForgotPasswordPage() {
  const { sendPasswordResetCode } = useAuth()
  const [email, setEmail] = useState<string>()
  const [formError, setFormError] = useState<string>()
  const { register, handleSubmit, formState } = useForm<EmailValues>({ resolver: zodResolver(emailSchema) })

  const submit = handleSubmit(async (values) => {
    setFormError(undefined)
    try {
      await sendPasswordResetCode(values.email)
      setEmail(values.email)
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : unreachable)
    }
  })

  if (email) {
    return <ResetStep email={email} onChangeEmail={() => setEmail(undefined)} />
  }

  return (
    <AuthLayout
      heading="Reset your password"
      lede="Enter the email you use for Rentbook. We'll send a code to set a new password."
      below={
        <p>
          Remembered it? <Link to="/login">Sign in</Link>
        </p>
      }
    >
      <form className={authStyles.form} onSubmit={submit} noValidate>
        <FormError>{formError}</FormError>
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          inputMode="email"
          error={formState.errors.email?.message}
          {...register('email')}
        />
        <div className={authStyles.actions}>
          <Button type="submit" block busy={formState.isSubmitting}>
            {formState.isSubmitting ? 'Sending the code' : 'Email me a code'}
          </Button>
        </div>
      </form>
    </AuthLayout>
  )
}

function ResetStep({ email, onChangeEmail }: { email: string; onChangeEmail: () => void }) {
  const { resetPassword, sendPasswordResetCode } = useAuth()
  const [formError, setFormError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const [resending, setResending] = useState(false)
  const { register, handleSubmit, formState, setError } = useForm<ResetValues>({ resolver: zodResolver(resetSchema) })

  const submit = handleSubmit(async ({ code, password }) => {
    setFormError(undefined)
    try {
      await resetPassword({ email, code, password })
    } catch (error) {
      if (error instanceof ApiError && (error.code === 'wrong_code' || error.code === 'code_expired')) {
        setError('code', { message: error.message })
      } else {
        setFormError(error instanceof ApiError ? error.message : unreachable)
      }
    }
  })

  const resend = async () => {
    setFormError(undefined)
    setNotice(undefined)
    setResending(true)
    try {
      await sendPasswordResetCode(email)
      setNotice('If a new code was due, it is on its way. The newest code is the one that works.')
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : unreachable)
    } finally {
      setResending(false)
    }
  }

  return (
    <AuthLayout
      heading="Check your email"
      lede={`If ${email} has a Rentbook account, we've sent it a 6-digit code. It works for 10 minutes.`}
      below={
        <p>
          Wrong address?{' '}
          <button type="button" className={authStyles.inlineAction} onClick={onChangeEmail}>
            Change your email
          </button>
        </p>
      }
    >
      <form className={authStyles.form} onSubmit={submit} noValidate>
        <FormError>{formError}</FormError>
        <TextField
          label="Code"
          autoComplete="one-time-code"
          inputMode="numeric"
          maxLength={6}
          autoFocus
          hint={notice ?? "Can't find it? Look in spam, or send a new one."}
          error={formState.errors.code?.message}
          {...register('code')}
        />
        <TextField
          label="New password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters. You'll be signed out everywhere else."
          error={formState.errors.password?.message}
          {...register('password')}
        />
        <div className={authStyles.actions}>
          <Button type="submit" block busy={formState.isSubmitting}>
            {formState.isSubmitting ? 'Saving your password' : 'Set new password'}
          </Button>
          <Button type="button" variant="secondary" block busy={resending} onClick={resend}>
            {resending ? 'Sending' : 'Send a new code'}
          </Button>
        </div>
      </form>
    </AuthLayout>
  )
}
