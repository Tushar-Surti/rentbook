import type { ReactNode } from 'react'
import styles from './Stamp.module.css'

/**
 * Stamp Violet's only use: a payment Razorpay has confirmed. {@code landing} plays the system's one
 * authored motion, for the moment that confirmation arrives while someone is looking.
 */
export function Stamp({ size = 'mark', landing = false, children }: {
  size?: 'mark' | 'slip'
  landing?: boolean
  children: ReactNode
}) {
  return (
    <span className={[styles.stamp, styles[size], landing ? styles.landing : ''].filter(Boolean).join(' ')}>
      {children}
    </span>
  )
}
