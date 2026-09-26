import { AnimatePresence, domAnimation, LazyMotion, m, useInView, useReducedMotion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'
import { Sheet } from '../design/Sheet'
import { Stamp } from '../design/Stamp'
import styles from './Scenes.module.css'

/*
 * The scenes a month of the sample rent book opens into. Every figure here is the sample's: Lata Iyer
 * lets Bed A in Room 201 of Sunrise PG to Asha Rao for ₹12,500 a month. GSAP scrubs the writing
 * (anything marked data-ink); Framer Motion plays the moments that happen once, like the stamp.
 */

/**
 * 1 Sept. The signature: the landlord's original and the tenant's canary duplicate lie edge to edge as
 * one torn spread, and a single pen stroke runs under both amounts, writing them at the same moment.
 * HomePage lays the stroke's path between the two amounts once the spread has its size.
 */
export function CarbonCopyScene() {
  return (
    <div className={styles.spread} data-scene="copy">
      <div className={styles.original} aria-label="Lata's rent book" role="group">
        <p className={styles.sheetTitle}>Lata's book: Bed A, Room 201</p>
        <div className={styles.copyRow}>
          <span className="entry" data-ink>
            Asha Rao
          </span>
          <span className={`${styles.copyAmount} entry num`} data-ink data-amount="original">
            ₹12,500
          </span>
        </div>
        <p className={styles.small}>
          Rent for September, due{' '}
          <span className="entry" data-ink>
            5 Sept
          </span>
        </p>
      </div>

      <div className={styles.duplicate} aria-label="Asha's slip" role="group">
        <p className={styles.sheetTitle}>Asha's slip: Rent for September</p>
        <p className={`${styles.display} entry num`} data-ink data-amount="duplicate">
          ₹12,500
        </p>
        <p className={styles.small}>
          Due{' '}
          <span className="entry" data-ink>
            5 Sept
          </span>
        </p>
      </div>

      {/* The pen's one stroke, from under the landlord's figure, across the tear, to under the tenant's. */}
      <svg className={styles.stroke} aria-hidden="true">
        <path data-stroke />
        <circle data-tip r="4" />
      </svg>
    </div>
  )
}

/** 2 Sept. Reminders go out before, on and after the due day, each only once. */
export function ReminderScene() {
  return (
    <div className={styles.reminders} data-scene="list">
      <ol className={styles.ruled}>
        {[
          ['2 Sept', 'Rent of ₹12,500 is due on 5 Sept.', 'Three days before'],
          ['5 Sept', 'Rent of ₹12,500 is due today.', 'On the day'],
          ['8 Sept', 'Rent of ₹12,500 is 3 days late.', 'Only if still unpaid'],
        ].map(([day, text, when]) => (
          <li key={day} data-item>
            <time className="entry num">{day}</time>
            <span className="entry">{text}</span>
            <span className={styles.small}>{when}</span>
          </li>
        ))}
      </ol>
    </div>
  )
}

/** 5 Sept. Paid counts only once Razorpay confirms it; then the stamp lands on both copies together. */
export function PaidScene() {
  const ref = useRef<HTMLDivElement>(null)
  const inView = useInView(ref, { once: true, amount: 0.6 })
  const reduced = useReducedMotion()
  const [paid, setPaid] = useState(false)

  useEffect(() => {
    if (!inView) return
    if (reduced) {
      setPaid(true)
      return
    }
    const bank = window.setTimeout(() => setPaid(true), 1400)
    return () => window.clearTimeout(bank)
  }, [inView, reduced])

  return (
    <LazyMotion features={domAnimation} strict>
      <div ref={ref} className={styles.paid}>
        <div className={styles.paidPair}>
          <Sheet tone={paid ? 'original' : 'duplicate'} className={styles.paidSlip} aria-label="Asha's slip">
            <p className={styles.sheetTitle}>Asha's slip: Rent for September</p>
            <div className={styles.stamped}>
              <p className={`${styles.display} entry num`}>₹12,500</p>
              <span className={styles.slipStamp}>
                {paid && (
                  <Stamp size="slip" landing={!reduced}>
                    Paid
                  </Stamp>
                )}
              </span>
            </div>
            <AnimatePresence mode="wait" initial={false}>
              <m.p
                key={paid ? 'paid' : 'waiting'}
                className={styles.status}
                initial={{ opacity: 0, y: reduced ? 0 : 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: reduced ? 0 : -6 }}
                transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
              >
                {paid ? 'Confirmed by Razorpay. Receipt 0001 is ready.' : 'Waiting for the bank to confirm'}
              </m.p>
            </AnimatePresence>
          </Sheet>

          <Sheet className={styles.paidBook} aria-label="Lata's register">
            <p className={styles.sheetTitle}>Lata's register: Sunrise PG</p>
            <div className={styles.registerRow}>
              <span>Bed A</span>
              <span className="entry">Asha Rao</span>
              <span className="entry num">₹12,500</span>
              <span className={styles.registerMark}>
                {paid ? <Stamp landing={!reduced}>Paid</Stamp> : <span className={styles.due}>Due today</span>}
              </span>
            </div>
            <p className={styles.liveLine} role="status">
              {paid ? "Razorpay confirmed Asha Rao's payment of ₹12,500." : '\u00a0'}
            </p>
            <dl className={styles.split}>
              <div>
                <dt>To Lata's bank</dt>
                <dd className="entry num">₹12,250</dd>
              </div>
              <div>
                <dt>Rentbook's fee, 2%</dt>
                <dd className="entry num">₹250</dd>
              </div>
            </dl>
          </Sheet>
        </div>
      </div>
    </LazyMotion>
  )
}

/** 5 Sept. A numbered receipt with everything an HRA claim asks for. */
export function ReceiptScene() {
  return (
    <div className={styles.receiptStage} data-scene="receipt">
      <Sheet className={styles.receipt} aria-label="Rent receipt 0001" data-receipt>
        <div className={styles.receiptHead}>
          <div>
            <p className={styles.receiptTitle}>Rent receipt</p>
            <p className={styles.small}>
              No. <span className="entry num">0001</span> Date <span className="entry">5 Sept 2026</span>
            </p>
          </div>
          <Stamp size="slip">Paid</Stamp>
        </div>
        <p className={styles.receiptBody}>
          Received from <span className="entry">Asha Rao</span> the sum of <span className="entry num">₹12,500</span>{' '}
          (Rupees Twelve Thousand Five Hundred only) towards rent for September 2026.
        </p>
        <dl className={styles.receiptFacts}>
          <div>
            <dt>For</dt>
            <dd className="entry">Bed A, Room 201, Sunrise PG, Bengaluru 560095</dd>
          </div>
          <div>
            <dt>Landlord's PAN</dt>
            <dd className="entry">ABCPI1234K</dd>
          </div>
        </dl>
      </Sheet>
    </div>
  )
}

/** 11 Sept. One thread both of them write in, with the photo pinned in as a print. */
export function RepairScene() {
  return (
    <div className={styles.thread} data-scene="list">
      <div data-item className={styles.message}>
        <p className={styles.byline}>
          Asha Rao <span className={styles.small}>11 Sept, 8:10 am</span>
        </p>
        <p className="entry">Kitchen tap has been dripping all night. Floor by the sink is wet by morning.</p>
        <p className={styles.attachment}>
          Photo attached: <span className="entry">kitchen-tap.jpg</span>
        </p>
      </div>
      <div data-item className={styles.message}>
        <p className={styles.byline}>
          Lata Iyer <span className={styles.small}>11 Sept, 9:02 am</span>
        </p>
        <p className="entry">The plumber comes at 10 tomorrow. Keep the valve under the sink closed tonight.</p>
      </div>
      <p data-item className={styles.statusLine}>
        Lata is having it fixed. <span className={styles.small}>11 Sept, 9:03 am</span>
      </p>
    </div>
  )
}

/** 14 Sept. The lease's documents, seen only by its two people. */
export function ShelfScene() {
  return (
    <ul className={styles.shelf} data-scene="list">
      {[
        ['Lease agreement', 'lease-agreement.pdf', 'Filed by Lata on 14 Sept'],
        ['ID document', 'aadhaar.pdf', 'Filed by Asha on 14 Sept'],
        ['Rent receipt', 'rentbook-receipt-0001.pdf', 'Issued on 5 Sept'],
      ].map(([type, file, note]) => (
        <li key={file} data-item>
          <span className={styles.docType}>{type}</span>
          <span className="entry">{file}</span>
          <span className={styles.small}>{note}</span>
        </li>
      ))}
    </ul>
  )
}

/** 20 Sept. Paid in cash: the landlord records it, and the receipt says so. */
export function CashScene() {
  return (
    <div className={styles.cash} data-scene="list">
      <div className={styles.cashRow} data-item>
        <span>Electricity for September</span>
        <span className="entry num">₹1,800</span>
        <Stamp>Paid</Stamp>
      </div>
      <p className={styles.small} data-item>
        Receipt <span className="entry num">0002</span>: received in cash on <span className="entry">20 Sept</span>,
        recorded by the landlord. No fee on money paid to Lata directly.
      </p>
    </div>
  )
}
