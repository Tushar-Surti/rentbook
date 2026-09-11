import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { api, ApiError } from '../api/client'
import type { LeaseView, TicketCategory, TicketPriority, TicketThread } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Button } from '../design/Button'
import { FormError, SelectField, TextAreaField, TextField } from '../design/Field'
import { CATEGORY_LABELS, firstName, PRIORITY_LABELS } from '../maintenance/labels'
import { PhotoPicker } from '../maintenance/PhotoPicker'
import { usePhotoUploads } from '../maintenance/usePhotoUploads'
import styles from './NewRequestPage.module.css'
import { tenantHomeQuery } from './queries'

const categories = Object.keys(CATEGORY_LABELS) as [TicketCategory, ...TicketCategory[]]
const priorities = Object.keys(PRIORITY_LABELS) as [TicketPriority, ...TicketPriority[]]

const schema = z.object({
  title: z.string().trim().min(3, 'Say what needs fixing, in a few words').max(140),
  category: z.enum(categories),
  priority: z.enum(priorities),
  body: z.string().trim().min(1, 'Describe the problem').max(4000),
})

type Values = z.infer<typeof schema>

export function NewRequestPage() {
  const home = useQuery(tenantHomeQuery)
  if (home.isPending) return <SessionLoading />
  const lease = home.data?.lease
  if (!lease || lease.status === 'ENDED') {
    return <p className={styles.lede}>When you have an active lease, you can report problems here.</p>
  }
  return <NewRequestForm lease={lease} />
}

/** Reporting a problem: what, how soon, in the tenant's own words, with photos if they help. */
function NewRequestForm({ lease }: { lease: LeaseView }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const photos = usePhotoUploads(lease.id)
  const [formError, setFormError] = useState<string>()
  const landlord = firstName(lease.landlord.fullName)
  const { register, handleSubmit, formState } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { category: 'PLUMBING', priority: 'NORMAL' },
  })

  const submit = handleSubmit(async (values) => {
    if (photos.busy) {
      setFormError('Wait for the photos to finish uploading.')
      return
    }
    setFormError(undefined)
    try {
      const thread = await api<TicketThread>('/tickets', {
        method: 'POST',
        json: { ...values, leaseId: lease.id, photoIds: photos.ids },
      })
      photos.clear()
      queryClient.setQueryData(['ticket', thread.ticket.id], thread)
      await queryClient.invalidateQueries({ queryKey: ['tickets'] })
      navigate(`/t/requests/${thread.ticket.id}`)
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
    }
  })

  return (
    <div className={styles.wrap}>
      <div className={styles.intro}>
        <h1 className={styles.heading}>Report a problem</h1>
        <p className={styles.lede}>
          {landlord} sees it straight away, and gets a text too if it's urgent. Add photos if they help.
        </p>
      </div>
      <form className={styles.form} onSubmit={submit} noValidate>
        <FormError>{formError}</FormError>
        <TextField
          label="What needs fixing"
          placeholder="Kitchen tap leaking"
          autoComplete="off"
          error={formState.errors.title?.message}
          {...register('title')}
        />
        <div className={styles.pair}>
          <SelectField
            label="What kind of problem"
            options={categories.map((value) => ({ value, label: CATEGORY_LABELS[value] }))}
            {...register('category')}
          />
          <SelectField
            label="How soon"
            options={priorities.map((value) => ({ value, label: PRIORITY_LABELS[value] }))}
            {...register('priority')}
          />
        </div>
        <TextAreaField
          label="Describe it"
          hint="Where it is, since when, and anything you've tried."
          error={formState.errors.body?.message}
          {...register('body')}
        />
        <PhotoPicker photos={photos} />
        <div>
          <Button type="submit" busy={formState.isSubmitting}>
            {formState.isSubmitting ? 'Sending' : `Send to ${landlord}`}
          </Button>
        </div>
      </form>
    </div>
  )
}
