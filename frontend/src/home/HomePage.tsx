import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { useLayoutEffect, useRef, type ReactNode } from 'react'
import { Link } from 'react-router'
import { Brand } from '../design/Brand'
import { LinkButton } from '../design/Button'
import { Sheet } from '../design/Sheet'
import { Stamp } from '../design/Stamp'
import styles from './HomePage.module.css'
import { CarbonCopyScene, CashScene, PaidScene, ReceiptScene, RepairScene, ReminderScene, ShelfScene } from './Scenes'
import { useSmoothScroll } from './useSmoothScroll'

gsap.registerPlugin(ScrollTrigger)

type Row = { date: string; what: string; amount?: string; scene: ReactNode; caption: string }

// One sample month of one lease. Every name and figure is made up, and the page says so.
const MONTH: Row[] = [
  {
    date: '1 Sept',
    what: 'Rent for September',
    amount: '₹12,500',
    scene: <CarbonCopyScene />,
    caption:
      "Rent goes onto the book by itself each month. Lata's register and Asha's slip are one record, so the line is written once and both of them read it.",
  },
  {
    date: '2 Sept',
    what: 'Reminder sent to Asha',
    scene: <ReminderScene />,
    caption: 'Reminders go out by email and SMS before, on and after the due day, and never twice.',
  },
  {
    date: '5 Sept',
    what: 'Paid through Razorpay',
    amount: '₹12,500',
    scene: <PaidScene />,
    caption:
      "Asha pays by UPI or card from her phone. It counts only when Razorpay's signed confirmation reaches Rentbook; then the stamp lands on both screens, and Lata's share goes straight to her bank.",
  },
  {
    date: '5 Sept',
    what: 'Receipt 0001 issued',
    scene: <ReceiptScene />,
    caption: "Every confirmed payment gets a numbered PDF receipt with the amount in words and the landlord's PAN: what an HRA claim asks for.",
  },
  {
    date: '11 Sept',
    what: 'Kitchen tap leaking',
    scene: <RepairScene />,
    caption: 'Asha reports it with a photo; Lata replies in the same thread and moves it along. Both screens update as either of them writes.',
  },
  {
    date: '14 Sept',
    what: 'Lease agreement filed',
    scene: <ShelfScene />,
    caption: "Each lease keeps its own shelf of documents. Only Lata and Asha can see it; nobody else can even find it.",
  },
  {
    date: '20 Sept',
    what: 'Electricity, paid in cash',
    amount: '₹1,800',
    scene: <CashScene />,
    caption: "Paid in cash or straight to Lata's UPI? She records it, the line is marked paid for both of them, and Asha still gets her receipt.",
  },
]

/** The public front page: the pitch, then a sample month of one rent book, row by row. */
export function HomePage() {
  const root = useRef<HTMLDivElement>(null)
  useSmoothScroll()

  useLayoutEffect(() => {
    const media = gsap.matchMedia(root)
    // The stroke under both amounts is laid out from where the amounts actually sit, and again on resize.
    const copy = root.current?.querySelector<HTMLElement>('[data-scene="copy"]')
    const layStroke = copy ? () => layPenStroke(copy) : () => 0
    layStroke()
    window.addEventListener('resize', layStroke)

    media.add('(prefers-reduced-motion: no-preference)', () => {
      const ease = 'expo.out'
      const hidden = 'inset(-10% 100% -10% 0)'
      const started = 'inset(-10% 62% -10% 0)'
      const shown = 'inset(-10% 0% -10% 0)'

      // The pen writes the headline, and its last line swells from ledger width to display width.
      const intro = gsap.timeline({ defaults: { ease } })
      intro
        .fromTo('[data-line]', { clipPath: started }, { clipPath: shown, duration: 0.8, stagger: 0.16 })
        .fromTo('[data-swell]', { fontStretch: '82%' }, { fontStretch: '118%', duration: 0.9, ease: 'power3.inOut' }, 0.35)
        .fromTo('[data-lede]', { opacity: 0 }, { opacity: 1, duration: 0.5 }, 0.55)
        .fromTo('[data-hero-ink]', { clipPath: hidden }, { clipPath: shown, duration: 0.55, stagger: 0.05 }, 0.5)

      // The month's progress rule runs down the date column as the ledger scrolls.
      gsap.fromTo(
        '[data-rail]',
        { scaleY: 0 },
        { scaleY: 1, ease: 'none', scrollTrigger: { trigger: '[data-ledger]', start: 'top 60%', end: 'bottom 60%', scrub: true } },
      )

      // Each row's own entry is written as it reaches the reading line.
      gsap.utils.toArray<HTMLElement>('[data-row]').forEach((row) => {
        gsap.fromTo(
          row.querySelectorAll('[data-entry]'),
          { clipPath: hidden },
          { clipPath: shown, duration: 0.8, ease, stagger: 0.1, scrollTrigger: { trigger: row, start: 'top 72%' } },
        )
      })

      // The signature: one scroll position drives the pen and both copies' ink, so they are written together.
      if (copy) {
        const stroke = copy.querySelector<SVGPathElement>('[data-stroke]')
        const tip = copy.querySelector<SVGCircleElement>('[data-tip]')
        const inks = copy.querySelectorAll<HTMLElement>('[data-ink]')
        const write = (progress: number) => {
          const length = stroke?.getTotalLength() ?? 0
          stroke?.style.setProperty('stroke-dasharray', `${length}`)
          stroke?.style.setProperty('stroke-dashoffset', `${length * (1 - progress)}`)
          const point = stroke && length ? stroke.getPointAtLength(length * progress) : null
          if (tip && point) {
            tip.setAttribute('cx', `${point.x}`)
            tip.setAttribute('cy', `${point.y}`)
            tip.style.opacity = progress > 0 && progress < 1 ? '1' : '0'
          }
          inks.forEach((ink) => (ink.style.clipPath = `inset(-10% ${(1 - progress) * 100}% -10% 0)`))
        }
        write(0)
        ScrollTrigger.create({
          trigger: copy,
          start: 'top 75%',
          end: 'center 45%',
          scrub: 0.6,
          onUpdate: (self) => write(self.progress),
          onRefresh: (self) => {
            layStroke()
            write(self.progress)
          },
        })
      }

      // Lists are written line by line, top to bottom.
      gsap.utils.toArray<HTMLElement>('[data-scene="list"]').forEach((list) => {
        gsap.fromTo(
          list.querySelectorAll('[data-item]'),
          { clipPath: 'inset(0% 0% 100% 0%)' },
          {
            clipPath: 'inset(0% 0% -10% 0%)',
            duration: 0.7,
            ease,
            stagger: 0.14,
            scrollTrigger: { trigger: list, start: 'top 75%' },
          },
        )
      })

      // The receipt tears off the pad and settles square.
      gsap.fromTo(
        '[data-receipt]',
        { clipPath: 'inset(0% 0% 100% 0%)', rotate: -2.5, y: -24 },
        {
          clipPath: 'inset(0% 0% -12% 0%)',
          rotate: 0,
          y: 0,
          duration: 1.1,
          ease,
          scrollTrigger: { trigger: '[data-scene="receipt"]', start: 'top 70%' },
        },
      )

      // The total's double rule is drawn, then the balance is written.
      gsap
        .timeline({ scrollTrigger: { trigger: '[data-total]', start: 'top 80%' } })
        .fromTo('[data-total-rule]', { scaleX: 0 }, { scaleX: 1, duration: 0.9, ease: 'power3.inOut' })
        .fromTo('[data-total-ink]', { clipPath: hidden }, { clipPath: shown, duration: 0.8, ease, stagger: 0.12 }, '-=0.3')
    })

    return () => {
      media.revert()
      window.removeEventListener('resize', layStroke)
    }
  }, [])

  return (
    <div ref={root} className={styles.page}>
      <header className={styles.top}>
        <Link to="/" className={styles.home} aria-label="Rentbook home">
          <Brand />
        </Link>
        <nav className={styles.nav} aria-label="Account">
          <Link to="/login" className={styles.signIn}>
            Sign in
          </Link>
          <LinkButton to="/register" variant="primary">
            Open your rent book
          </LinkButton>
        </nav>
      </header>

      <main>
        <section className={styles.hero} aria-labelledby="pitch">
          <div className={styles.pitch}>
            <h1 id="pitch" className={styles.headline}>
              <span data-line className={styles.line}>
                One rent book.
              </span>
              <span data-line className={styles.line}>
                Two people.
              </span>
              <span data-line data-swell className={`${styles.line} ${styles.swell}`}>
                Written once.
              </span>
            </h1>
            <p data-lede className={styles.lede}>
              Rentbook keeps a landlord and a tenant on the same page: one rent ledger, one repair thread and one shelf
              of documents, updated live for both of them. Made for flats and PG beds in India.
            </p>
            <div className={styles.paths}>
              <div className={styles.path}>
                <h2>For landlords</h2>
                <p>Add your flats, rooms and beds, invite tenants, and get rent straight into your own bank account.</p>
                <LinkButton to="/register" variant="primary">
                  Open your rent book
                </LinkButton>
              </div>
              <div className={styles.path}>
                <h2>For tenants</h2>
                <p>Your landlord's invite link brings you in. Pay from your phone and keep every receipt.</p>
                <LinkButton to="/login" variant="secondary">
                  Sign in
                </LinkButton>
              </div>
            </div>
          </div>

          <Sheet className={styles.heroBook} aria-label="A sample rent book">
            <div className={styles.bookHead}>
              <p className={styles.bookTitle}>Bed A, Room 201, Sunrise PG</p>
              <p className={styles.bookMonth}>September</p>
            </div>
            <table className={styles.bookTable}>
              <thead>
                <tr>
                  <th scope="col">Due</th>
                  <th scope="col">For</th>
                  <th scope="col" className={styles.right}>
                    Amount
                  </th>
                  <th scope="col">Status</th>
                </tr>
              </thead>
              <tbody>
                {[
                  ['1 Sept', 'Security deposit', '₹25,000', true],
                  ['5 Sept', 'Rent for September', '₹12,500', true],
                  ['28 Sept', 'Electricity for September', '₹1,800', false],
                ].map(([due, what, amount, paid]) => (
                  <tr key={what as string}>
                    <td>
                      <span data-hero-ink className="entry num">
                        {due}
                      </span>
                    </td>
                    <td>
                      <span data-hero-ink>{what}</span>
                    </td>
                    <td className={styles.right}>
                      <span data-hero-ink className="entry num">
                        {amount}
                      </span>
                    </td>
                    <td>{paid ? <Stamp>Paid</Stamp> : <span className={styles.upcoming}>Upcoming</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className={styles.bookNote}>A made-up sample. Asha, the tenant, reads this same page on her phone.</p>
          </Sheet>
        </section>

        <section className={styles.month} aria-labelledby="month-heading">
          <div className={styles.monthHead}>
            <h2 id="month-heading">A month in one rent book</h2>
            <p>A sample tenancy, row by row. The names, the flat and the amounts are made up.</p>
          </div>

          <div className={styles.ledgerWrap} data-ledger>
            <span className={styles.rail} aria-hidden="true">
              <span data-rail className={styles.railInk} />
            </span>
            <ol className={styles.ledger}>
            {MONTH.map((row) => (
              <li key={row.date + row.what} className={styles.row} data-row>
                <time className={`${styles.date} entry num`}>{row.date}</time>
                <div className={styles.body}>
                  <div className={styles.line2}>
                    <h3 data-entry className={styles.what}>
                      {row.what}
                    </h3>
                    {row.amount && (
                      <span data-entry className={`${styles.amount} entry num`}>
                        {row.amount}
                      </span>
                    )}
                  </div>
                  <div className={styles.scene}>{row.scene}</div>
                  <p className={styles.caption}>{row.caption}</p>
                </div>
              </li>
            ))}
            </ol>
          </div>

          <div className={styles.total} data-total>
            <span data-total-rule className={styles.totalRule} aria-hidden="true" />
            <div className={styles.totalLine}>
              <span data-total-ink className={styles.totalLabel}>
                Outstanding at the end of September
              </span>
              <span data-total-ink className={`${styles.totalAmount} entry num`}>
                ₹0
              </span>
            </div>
            <div className={styles.close}>
              <h2 className={styles.closeHeading}>Start the book for your home.</h2>
              <div className={styles.paths}>
                <div className={styles.path}>
                  <h3>Letting a flat or PG beds?</h3>
                  <p>
                    Open a rent book for one property, add a room, invite a tenant, and watch the same page fill in on
                    both phones. Payments run in Razorpay's test mode, so no real money moves while you try it.
                  </p>
                  <LinkButton to="/register" variant="primary">
                    Open your rent book
                  </LinkButton>
                </div>
                <div className={styles.path}>
                  <h3>Renting?</h3>
                  <p>Your landlord's invite link is your way in. Once you've joined, sign in to pay and see your receipts.</p>
                  <LinkButton to="/login" variant="secondary">
                    Sign in
                  </LinkButton>
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>

      <footer className={styles.footer}>
        <Brand />
        <p>Rentbook is a shared rent book for landlords and tenants. Payments run in Razorpay's test mode.</p>
      </footer>
    </div>
  )
}

/**
 * Lays the pen's path from under the landlord's figure, across the tear, to under the tenant's, in the
 * spread's own pixels, and returns its length. Side by side it runs right; stacked on a phone it drops.
 */
function layPenStroke(spread: HTMLElement): number {
  const svg = spread.querySelector('svg')
  const path = spread.querySelector<SVGPathElement>('[data-stroke]')
  const from = spread.querySelector<HTMLElement>('[data-amount="original"]')
  const to = spread.querySelector<HTMLElement>('[data-amount="duplicate"]')
  if (!svg || !path || !from || !to) return 0
  const box = spread.getBoundingClientRect()
  const a = from.getBoundingClientRect()
  const b = to.getBoundingClientRect()
  svg.setAttribute('viewBox', `0 0 ${box.width} ${box.height}`)
  const x1 = a.left - box.left
  const y1 = a.bottom - box.top + 5
  const x2 = b.right - box.left
  const y2 = b.bottom - box.top + 6
  const x3 = a.right - box.left
  if (b.top >= a.bottom) {
    // Stacked: under the landlord's figure, down the spread's right margin, and back in under the tenant's.
    const edge = box.width - 14
    const x4 = b.left - box.left
    path.setAttribute(
      'd',
      `M ${x1} ${y1} L ${x3} ${y1} Q ${edge} ${y1} ${edge} ${y1 + 18} L ${edge} ${y2 - 18} Q ${edge} ${y2} ${edge - 18} ${y2} L ${x4} ${y2}`,
    )
    return path.getTotalLength()
  }
  // Side by side: under the landlord's figure, across the tear, and on under the tenant's.
  const bend = Math.max(24, Math.abs(y2 - y1) / 2)
  path.setAttribute(
    'd',
    `M ${x1} ${y1} L ${x3} ${y1} C ${x3 + bend} ${y1}, ${b.left - box.left - bend} ${y2}, ${b.left - box.left} ${y2} L ${x2} ${y2}`,
  )
  return path.getTotalLength()
}
