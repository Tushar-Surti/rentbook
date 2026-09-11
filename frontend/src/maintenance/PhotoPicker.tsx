import { useId } from 'react'
import { Button } from '../design/Button'
import buttonStyles from '../design/Button.module.css'
import { Icon } from '../design/Icon'
import styles from './PhotoPicker.module.css'
import type { PhotoUploads } from './usePhotoUploads'

/** Photos to send, shown as prints while they travel to storage. */
export function PhotoPicker({ photos }: { photos: PhotoUploads }) {
  const inputId = useId()
  return (
    <div className={styles.picker}>
      {photos.items.length > 0 && (
        <ul className={styles.pending} aria-label="Photos to send">
          {photos.items.map((photo) => (
            <li key={photo.key} className={styles.print} data-state={photo.state}>
              <img src={photo.preview} alt={photo.name} />
              <span className={styles.state} role={photo.state === 'failed' ? 'alert' : undefined}>
                {photo.state === 'uploading' ? 'Uploading' : photo.state === 'failed' ? photo.error : 'Ready'}
              </span>
              <Button
                variant="quiet"
                className={styles.remove}
                aria-label={`Remove ${photo.name}`}
                onClick={() => photos.remove(photo.key)}
              >
                Remove
              </Button>
            </li>
          ))}
        </ul>
      )}
      {!photos.full && (
        <label htmlFor={inputId} className={`${buttonStyles.button} ${buttonStyles.secondary} ${styles.attach}`}>
          <Icon name="plus" size={18} />
          Attach photos
          <input
            id={inputId}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            className="visually-hidden"
            onChange={(event) => {
              photos.add(event.target.files)
              event.target.value = ''
            }}
          />
        </label>
      )}
      <p className={styles.hint}>Up to six photos, 10 MB each.</p>
    </div>
  )
}
