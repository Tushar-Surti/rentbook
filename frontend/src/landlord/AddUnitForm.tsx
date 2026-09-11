import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { api, ApiError } from '../api/client'
import type { Property, Unit } from '../api/types'
import { Button } from '../design/Button'
import { FormError, SelectField, TextField } from '../design/Field'
import { bedLabels, toPaise } from '../lib/format'
import styles from './AddUnitForm.module.css'

const schema = z.object({
  kind: z.enum(['FLAT', 'ROOM', 'BED']),
  label: z.string().trim().min(1, 'Give it a label').max(60),
  roomId: z.string(),
  beds: z.string().refine((value) => /^(|[0-9]|1[0-2])$/.test(value.trim()), 'Up to 12 beds'),
  rent: z.string().refine((value) => value.trim() === '' || toPaise(value) !== null, 'Enter an amount like 12,500'),
})

type Values = z.infer<typeof schema>

const PLACEHOLDERS: Record<Values['kind'], string> = { FLAT: 'Flat 3B', ROOM: 'Room 201', BED: 'Bed D' }

/** Adds a flat, a room (with its beds in one go), or a bed to an existing room. */
export function AddUnitForm({ property }: { property: Property }) {
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string>()
  const [added, setAdded] = useState('')
  const rooms = property.units.filter((unit) => unit.kind === 'ROOM')
  const { register, handleSubmit, formState, watch, reset, setError } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      kind: property.kind === 'PG' ? 'ROOM' : 'FLAT',
      label: '',
      roomId: rooms[0]?.id ?? '',
      beds: property.kind === 'PG' ? '2' : '',
      rent: '',
    },
  })
  const kind = watch('kind')

  const submit = handleSubmit(async ({ kind, label, roomId, beds, rent }) => {
    setFormError(undefined)
    setAdded('')
    if (kind === 'BED' && !roomId) {
      setError('roomId', { message: 'Add the room first, then its beds' })
      return
    }
    const defaultRentPaise = rent.trim() ? toPaise(rent) : null
    const addUnit = (json: object) => api<Unit>(`/properties/${property.id}/units`, { method: 'POST', json })
    try {
      const unit = await addUnit({ kind, label, parentUnitId: kind === 'BED' ? roomId : null, defaultRentPaise })
      const bedCount = kind === 'ROOM' ? Number(beds || 0) : 0
      for (const bed of bedLabels(bedCount)) {
        await addUnit({ kind: 'BED', label: bed, parentUnitId: unit.id, defaultRentPaise })
      }
      setAdded(bedCount > 0 ? `Added ${label} with ${bedCount} beds.` : `Added ${label}.`)
      reset({ kind, label: '', roomId: kind === 'BED' ? roomId : (rooms[0]?.id ?? ''), beds, rent })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'label_taken') {
        setError('label', { message: error.message })
      } else {
        setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
      }
    } finally {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['property', property.id] }),
        queryClient.invalidateQueries({ queryKey: ['board'] }),
      ])
    }
  })

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      <FormError>{formError}</FormError>
      <div className={styles.fields}>
        <SelectField
          label="What"
          options={[
            { value: 'FLAT', label: 'A flat' },
            { value: 'ROOM', label: 'A room' },
            { value: 'BED', label: 'A bed in a room' },
          ]}
          {...register('kind')}
        />
        {kind === 'BED' && (
          <SelectField
            label="In room"
            options={rooms.length ? rooms.map((room) => ({ value: room.id, label: room.label })) : [{ value: '', label: 'No rooms yet' }]}
            error={formState.errors.roomId?.message}
            {...register('roomId')}
          />
        )}
        <TextField
          label="Label"
          placeholder={PLACEHOLDERS[kind]}
          autoComplete="off"
          error={formState.errors.label?.message}
          {...register('label')}
        />
        {kind === 'ROOM' && (
          <TextField
            label="Beds in it"
            inputMode="numeric"
            hint="Leave empty to let the room whole."
            error={formState.errors.beds?.message}
            {...register('beds')}
          />
        )}
        <TextField
          label={kind === 'ROOM' ? 'Asking rent per bed' : 'Asking rent'}
          prefix="₹"
          inputMode="decimal"
          hint="Optional. Prefills the invite."
          error={formState.errors.rent?.message}
          {...register('rent')}
        />
      </div>
      <div className={styles.actions}>
        {/* The only next step on an empty page, so it gets the Carbon primary until a unit exists. */}
        <Button type="submit" variant={property.units.length === 0 ? 'primary' : 'secondary'} busy={formState.isSubmitting}>
          {formState.isSubmitting ? 'Adding' : 'Add'}
        </Button>
        <p className={styles.added} role="status">
          {added}
        </p>
      </div>
    </form>
  )
}
