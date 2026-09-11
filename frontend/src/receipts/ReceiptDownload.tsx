import { useState, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import type { Receipt } from '../api/types'
import { Button } from '../design/Button'
import styles from './Receipts.module.css'
import { saveReceipt } from './receipts'

/** Downloads one receipt's PDF; its accessible name always carries the receipt number. */
export function ReceiptDownload({ receipt, variant = 'quiet', className, children }: {
  receipt: Receipt
  variant?: 'secondary' | 'quiet'
  className?: string
  children: ReactNode
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  const download = async () => {
    setBusy(true)
    setError(undefined)
    try {
      await saveReceipt(receipt)
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : "Couldn't download the receipt. Try again.")
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Button
        variant={variant}
        className={className}
        busy={busy}
        aria-label={`Download receipt ${receipt.number}`}
        onClick={() => void download()}
      >
        {children}
      </Button>
      {error && (
        <span role="alert" className={styles.error}>
          {error}
        </span>
      )}
    </>
  )
}
