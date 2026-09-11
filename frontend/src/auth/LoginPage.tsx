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

const schema = z.object({
  email: z.email('Enter the email you signed up with'),
  password: z.string().min(1, 'Enter your password'),
})

type Values = z.infer<typeof schema>

export function LoginPage() {
  const { login } = useAuth()
  const [formError, setFormError] = useState<string>()
  const { register, handleSubmit, formState } = useForm<Values>({ resolver: zodResolver(schema) })

  const submit = handleSubmit(async ({ email, password }) => {
    setFormError(undefined)
    try {
      await login(email, password)
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
    }
  })

  return (
    <AuthLayout
      heading="Sign in"
      lede="Landlords and tenants use the same door."
      below={
        <>
          <p>
            New landlord? <Link to="/register">Create your account</Link>
          </p>
          <p>Tenants join from the invite link their landlord sends.</p>
        </>
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
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          error={formState.errors.password?.message}
          {...register('password')}
        />
        <div className={authStyles.actions}>
          <Button type="submit" block busy={formState.isSubmitting}>
            {formState.isSubmitting ? 'Signing in' : 'Sign in'}
          </Button>
        </div>
      </form>
    </AuthLayout>
  )
}
