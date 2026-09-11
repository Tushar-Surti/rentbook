import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { api, ApiError } from '../api/client'
import { Button } from '../design/Button'
import { FormError, SelectField, TextField } from '../design/Field'
import { isoDaysFromToday, toPaise } from '../lib/format'
import styles from './AddChargeForm.module.css'

const schema = z.object({
  kind: z.enum(['UTILITY', 'OTHER']),
  description: z.string().trim().min(1, 'Say what the charge is for').max(160),
  amount: z.string().refine((value) => (toPaise(value) ?? 0) > 0, 'Enter the amount'),
  dueOn: z.string().min(1, 'Choose when it is due'),
})

type Values = z.infer<typeof schema>

/** One-off charges on top of rent, such as the month's electricity. Rent and deposit are added automatically. */
export function AddChargeForm({ leaseId }: { leaseId: string }) {
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string>()
  const [added, setAdded] = useState('')
  const { register, handleSubmit, formState, reset, watch } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { kind: 'UTILITY', description: '', amount: '', dueOn: isoDaysFromToday(7) },
  })
  const dueOn = watch('dueOn')

  const submit = handleSubmit(async ({ kind, description, amount, dueOn }) => {
    setFormError(undefined)
    setAdded('')
    try {
      await api(`/leases/${leaseId}/charges`, {
        method: 'POST',
        json: { kind, description, amountPaise: toPaise(amount), dueOn },
      })
      setAdded(`Added ${description.trim()}.`)
      reset({ kind, description: '', amount: '', dueOn: isoDaysFromToday(7) })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['ledger', leaseId] }),
        queryClient.invalidateQueries({ queryKey: ['board'] }),
      ])
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
    }
  })

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      <FormError>{formError}</FormError>
      <div className={styles.fields}>
        <SelectField
          label="What"
          options={[
            { value: 'UTILITY', label: 'Electricity or water' },
            { value: 'OTHER', label: 'Something else' },
          ]}
          {...register('kind')}
        />
        <TextField
          label="Description"
          placeholder="Electricity for September"
          autoComplete="off"
          error={formState.errors.description?.message}
          {...register('description')}
        />
        <TextField
          label="Amount"
          prefix="₹"
          inputMode="decimal"
          error={formState.errors.amount?.message}
          {...register('amount')}
        />
        <TextField
          label="Due on"
          type="date"
          data-empty={dueOn ? undefined : true}
          error={formState.errors.dueOn?.message}
          {...register('dueOn')}
        />
      </div>
      <div className={styles.actions}>
        <Button type="submit" busy={formState.isSubmitting}>
          {formState.isSubmitting ? 'Adding' : 'Add charge'}
        </Button>
        <p className={styles.added} role="status">
          {added}
        </p>
      </div>
    </form>
  )
}
