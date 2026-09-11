import type { ReactNode } from 'react'
import styles from './Mark.module.css'

/**
 * Status written beside an entry. These are plain marks; a payment Razorpay has confirmed gets the
 * violet {@link Stamp} instead, so the stamp keeps its meaning.
 */
export type MarkTone =
  | 'occupied'
  | 'vacant'
  | 'invited'
  | 'notice'
  | 'inactive'
  | 'upcoming'
  | 'due'
  | 'overdue'
  | 'waived'
  // Maintenance requests
  | 'working'
  | 'done'
  | 'urgent'

export function Mark({ tone, children }: { tone: MarkTone; children: ReactNode }) {
  return <span className={`${styles.mark} ${styles[tone]}`}>{children}</span>
}
