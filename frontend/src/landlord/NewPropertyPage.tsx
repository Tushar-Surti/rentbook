import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { api, ApiError } from '../api/client'
import type { Property } from '../api/types'
import { Button } from '../design/Button'
import { FormError, SelectField, TextField } from '../design/Field'
import styles from './NewPropertyPage.module.css'
import { boardQuery } from './queries'

const schema = z.object({
  name: z.string().trim().min(1, 'Give the property a name').max(120),
  kind: z.enum(['PG', 'APARTMENT', 'HOUSE']),
  addressLine: z.string().trim().min(1, 'Enter the street address').max(240),
  city: z.string().trim().min(1, 'Enter the city').max(80),
  pincode: z.string().trim().regex(/^[1-9]\d{5}$/, 'Enter a 6-digit PIN code'),
})

type Values = z.infer<typeof schema>

const kinds = [
  { value: 'PG', label: 'PG or hostel, let by room or bed' },
  { value: 'APARTMENT', label: 'Apartment building, let by flat' },
  { value: 'HOUSE', label: 'Independent house' },
]

export function NewPropertyPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const board = useQuery(boardQuery)
  const firstProperty = board.data?.properties.length === 0
  const [formError, setFormError] = useState<string>()
  const { register, handleSubmit, formState } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { kind: 'PG' },
  })

  const submit = handleSubmit(async (values) => {
    setFormError(undefined)
    try {
      const created = await api<Property>('/properties', { method: 'POST', json: values })
      await queryClient.invalidateQueries({ queryKey: ['board'] })
      navigate(`/l/p/${created.id}`)
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
    }
  })

  return (
    <div className={styles.wrap}>
      <div className={styles.intro}>
        <h1 className={styles.heading}>{firstProperty ? 'Add your first property' : 'Add a property'}</h1>
        <p className={styles.lede}>Start with the building. You'll add its flats, rooms and beds next.</p>
      </div>
      <form className={styles.form} onSubmit={submit} noValidate>
        <FormError>{formError}</FormError>
        <TextField
          label="Name"
          placeholder="Sunrise PG"
          autoComplete="off"
          error={formState.errors.name?.message}
          {...register('name')}
        />
        <SelectField label="What kind of place" options={kinds} error={formState.errors.kind?.message} {...register('kind')} />
        <TextField
          label="Street address"
          autoComplete="street-address"
          error={formState.errors.addressLine?.message}
          {...register('addressLine')}
        />
        <div className={styles.pair}>
          <TextField label="City" autoComplete="address-level2" error={formState.errors.city?.message} {...register('city')} />
          <TextField
            label="PIN code"
            inputMode="numeric"
            autoComplete="postal-code"
            maxLength={6}
            error={formState.errors.pincode?.message}
            {...register('pincode')}
          />
        </div>
        <div>
          <Button type="submit" busy={formState.isSubmitting}>
            {formState.isSubmitting ? 'Adding' : 'Add property'}
          </Button>
        </div>
      </form>
    </div>
  )
}
