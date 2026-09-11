import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Brand } from '../design/Brand'
import { Sheet } from '../design/Sheet'
import styles from './AuthLayout.module.css'

export function AuthLayout({ heading, lede, children, below }: {
  heading: string
  lede?: ReactNode
  children: ReactNode
  below?: ReactNode
}) {
  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <Link to="/" className={styles.home} aria-label="Rentbook home">
          <Brand />
        </Link>
      </header>
      <main className={styles.main}>
        <Sheet className={styles.sheet} aria-labelledby="auth-heading">
          <div className={styles.intro}>
            <h1 id="auth-heading" className={styles.heading}>
              {heading}
            </h1>
            {lede && <p className={styles.lede}>{lede}</p>}
          </div>
          {children}
        </Sheet>
        {below && <div className={styles.below}>{below}</div>}
      </main>
    </div>
  )
}
