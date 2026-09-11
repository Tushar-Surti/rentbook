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

type Values = z.infer<typeof schema>

export function RegisterPage() {
  const { register: createAccount } = useAuth()
  const [formError, setFormError] = useState<string>()
  const { register, handleSubmit, formState, setError } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { phone: '' },
  })

  const submit = handleSubmit(async ({ fullName, email, phone, password }) => {
    setFormError(undefined)
    try {
      await createAccount({ fullName, email, phone: phone ? `+91${phone}` : undefined, password })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'email_taken') {
        setError('email', { message: error.message })
      } else {
        setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
      }
    }
  })

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
            {formState.isSubmitting ? 'Creating your account' : 'Create account'}
          </Button>
        </div>
      </form>
    </AuthLayout>
  )
}
