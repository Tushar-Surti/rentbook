import { useId, useState } from 'react'
import type { IssuedInvite } from '../api/types'
import { Button } from '../design/Button'
import buttonStyles from '../design/Button.module.css'
import { Icon } from '../design/Icon'
import styles from './ShareLink.module.css'

/** The invite link is shown once, right after it is issued; only its hash is kept on the server. */
export function ShareLink({ issued }: { issued: IssuedInvite }) {
  const id = useId()
  const [copied, setCopied] = useState(false)
  const { invite, link } = issued
  const message = `Hi ${invite.tenantName}, here is your Rentbook invite for ${invite.unitLabel} at ${invite.propertyName}: ${link}`
  const phone = invite.phone?.replace(/\D/g, '') ?? ''
  const whatsapp = `https://wa.me/${phone}?text=${encodeURIComponent(message)}`

  const copy = async () => {
    await navigator.clipboard.writeText(link)
    setCopied(true)
  }

  const copyButton = (
    <Button variant="secondary" onClick={() => void copy()}>
      <Icon name={copied ? 'check' : 'copy'} />
      {copied ? 'Copied' : 'Copy link'}
    </Button>
  )

  return (
    <div className={styles.share}>
      <label htmlFor={id} className={styles.label}>
        Invite link for {invite.tenantName}
      </label>
      <input
        id={id}
        className={`${styles.link} entry`}
        readOnly
        value={link}
        onFocus={(event) => event.currentTarget.select()}
      />
      {phone ? (
        // With the tenant's number, WhatsApp straight to them is the natural way to hand the link over.
        <div className={styles.actions}>
          <a
            className={`${buttonStyles.button} ${buttonStyles.primary} ${styles.primaryAction}`}
            href={whatsapp}
            target="_blank"
            rel="noreferrer"
          >
            Send on WhatsApp
          </a>
          {copyButton}
        </div>
      ) : (
        <div className={styles.actions}>
          {copyButton}
          <a className={styles.whatsapp} href={whatsapp} target="_blank" rel="noreferrer">
            Send on WhatsApp
          </a>
        </div>
      )}
      <p className={styles.note} aria-live="polite">
        {copied ? 'Link copied. It works once and expires in 7 days.' : 'It works once and expires in 7 days.'}
      </p>
    </div>
  )
}
