import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../api/client'
import { Button } from '../design/Button'
import { FormError, TextField } from '../design/Field'
import { indianMobile } from '../lib/validation'
import { AuthLayout } from './AuthLayout'
import authStyles from './AuthLayout.module.css'
import { useAuth } from './AuthProvider'

const schema = z.object({
  fullName: z.string().trim().min(1, 'Enter your name').max(120),
  email: z.email('Enter an email you can receive invites and receipts on'),
  phone: indianMobile,
  password: z.string().min(8, 'Use at least 8 characters').max(72, 'Use 72 characters or fewer'),
})

const codeSchema = z.object({
  code: z.string().trim().regex(/^[0-9]{6}$/, 'Enter the 6-digit code from the email'),
})

type Values = z.infer<typeof schema>
type CodeValues = z.infer<typeof codeSchema>

const unreachable = 'Could not reach Rentbook. Check your connection.'

/** Two steps: the account details, then the code emailed to prove the address. */
export function RegisterPage() {
  const { sendSignupCode } = useAuth()
  const [details, setDetails] = useState<Values>()
  const [formError, setFormError] = useState<string>()
  const { register, handleSubmit, formState, setError } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { phone: '' },
  })

  const submit = handleSubmit(async (values) => {
    setFormError(undefined)
    try {
      await sendSignupCode(values.email)
      setDetails(values)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'email_taken') {
        setError('email', { message: error.message })
      } else {
        setFormError(error instanceof ApiError ? error.message : unreachable)
      }
    }
  })

  if (details) {
    return <CodeStep details={details} onChangeEmail={() => setDetails(undefined)} />
  }

  return (
    <AuthLayout
      heading="Open your rent book"
      lede="A landlord account. You'll add properties and invite tenants next."
      below={
        <p>
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      }
    >
      <form className={authStyles.form} onSubmit={submit} noValidate>
        <FormError>{formError}</FormError>
        <TextField label="Your name" autoComplete="name" error={formState.errors.fullName?.message} {...register('fullName')} />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          inputMode="email"
          hint="We'll email a code to check it's yours."
          error={formState.errors.email?.message}
          {...register('email')}
        />
        <TextField
          label="Mobile number (optional)"
          prefix="+91"
          type="tel"
          autoComplete="tel-national"
          inputMode="numeric"
          hint="For rent reminders by SMS."
          error={formState.errors.phone?.message}
          {...register('phone')}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters."
          error={formState.errors.password?.message}
          {...register('password')}
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

function CodeStep({ details, onChangeEmail }: { details: Values; onChangeEmail: () => void }) {
  const { register: createAccount, sendSignupCode } = useAuth()
  const [formError, setFormError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const [resending, setResending] = useState(false)
  const { register, handleSubmit, formState, setError } = useForm<CodeValues>({ resolver: zodResolver(codeSchema) })

  const submit = handleSubmit(async ({ code }) => {
    setFormError(undefined)
    const { fullName, email, phone, password } = details
    try {
      await createAccount({ fullName, email, phone: phone ? `+91${phone}` : undefined, password, code })
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
      await sendSignupCode(details.email)
      setNotice('A new code is on its way. The old one no longer works.')
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : unreachable)
    } finally {
      setResending(false)
    }
  }

  return (
    <AuthLayout
      heading="Check your email"
      lede={`We sent a 6-digit code to ${details.email}. It works for 10 minutes.`}
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
        <div className={authStyles.actions}>
          <Button type="submit" block busy={formState.isSubmitting}>
            {formState.isSubmitting ? 'Creating your account' : 'Create account'}
          </Button>
          <Button type="button" variant="secondary" block busy={resending} onClick={resend}>
            {resending ? 'Sending' : 'Send a new code'}
          </Button>
        </div>
      </form>
    </AuthLayout>
  )
}
