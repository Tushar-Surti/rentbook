import { useState } from 'react'
import { ApiError } from '../api/client'
import { uploadFile } from '../lib/upload'

const MAX_PHOTOS = 6
const MAX_BYTES = 10 * 1024 * 1024
const TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])

type Photo = {
  key: string
  name: string
  preview: string
  state: 'uploading' | 'ready' | 'failed'
  id?: string
  error?: string
}

export type PhotoUploads = ReturnType<typeof usePhotoUploads>

/** Photos start uploading the moment they're picked, so sending a message only has to attach them. */
export function usePhotoUploads(leaseId: string) {
  const [items, setItems] = useState<Photo[]>([])

  const update = (key: string, change: Partial<Photo>) =>
    setItems((current) => current.map((photo) => (photo.key === key ? { ...photo, ...change } : photo)))

  const add = (files: FileList | null) => {
    if (!files) return
    Array.from(files)
      .slice(0, Math.max(0, MAX_PHOTOS - items.length))
      .forEach((file) => {
        const key = crypto.randomUUID()
        const photo: Photo = { key, name: file.name, preview: URL.createObjectURL(file), state: 'uploading' }
        if (!TYPES.has(file.type)) {
          setItems((current) => [...current, { ...photo, state: 'failed', error: 'Use a JPEG, PNG or WebP photo.' }])
          return
        }
        if (file.size > MAX_BYTES) {
          setItems((current) => [...current, { ...photo, state: 'failed', error: 'Photos can be up to 10 MB.' }])
          return
        }
        setItems((current) => [...current, photo])
        uploadFile(file, leaseId, 'TICKET_PHOTO')
          .then((id) => update(key, { state: 'ready', id }))
          .catch((failure) =>
            update(key, {
              state: 'failed',
              error: failure instanceof ApiError ? failure.message : "Couldn't upload this photo.",
            }),
          )
      })
  }

  const remove = (key: string) =>
    setItems((current) => {
      const gone = current.find((photo) => photo.key === key)
      if (gone) URL.revokeObjectURL(gone.preview)
      return current.filter((photo) => photo.key !== key)
    })

  const clear = () =>
    setItems((current) => {
      current.forEach((photo) => URL.revokeObjectURL(photo.preview))
      return []
    })

  return {
    items,
    add,
    remove,
    clear,
    ids: items.flatMap((photo) => (photo.state === 'ready' && photo.id ? [photo.id] : [])),
    busy: items.some((photo) => photo.state === 'uploading'),
    full: items.length >= MAX_PHOTOS,
  }
}
