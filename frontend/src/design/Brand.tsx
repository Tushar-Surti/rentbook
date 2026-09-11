import styles from './Brand.module.css'

/** The wordmark: a receipt slip torn from the book, and the name printed beside it. */
export function Brand() {
  return (
    <span className={styles.brand}>
      <svg viewBox="0 0 32 32" width="22" height="22" aria-hidden="true" focusable="false">
        <path fill="var(--carbon)" d="M5 4h22v24H5z" />
        <g fill="var(--sheet)">
          <circle cx="8" cy="4" r="1.6" />
          <circle cx="12" cy="4" r="1.6" />
          <circle cx="16" cy="4" r="1.6" />
          <circle cx="20" cy="4" r="1.6" />
          <circle cx="24" cy="4" r="1.6" />
        </g>
        <path fill="none" stroke="var(--sheet)" strokeWidth="1.6" d="M9 12h14M9 17h14M9 22h8" />
        <circle cx="22" cy="22" r="3.2" fill="var(--canary)" />
      </svg>
      Rentbook
    </span>
  )
}
