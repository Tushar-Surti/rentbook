import type { HTMLAttributes } from 'react'
import styles from './Sheet.module.css'

type SheetProps = HTMLAttributes<HTMLElement> & {
  /** The white original, or the canary duplicate that shows what is due. */
  tone?: 'original' | 'duplicate'
  perforated?: boolean
}

/** A sheet of paper: square corners, a torn perforated top edge, and the system's only shadow. */
export function Sheet({ tone = 'original', perforated = true, className, ...rest }: SheetProps) {
  return (
    <section
      {...rest}
      className={[styles.sheet, styles[tone], perforated ? styles.perforated : '', className]
        .filter(Boolean)
        .join(' ')}
    />
  )
}
