import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import type { ChargeLine, LeaseView, MonthRent, PaymentEvent, TenantHome } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Button } from '../design/Button'
import { Icon } from '../design/Icon'
import { Mark } from '../design/Mark'
import { Sheet } from '../design/Sheet'
import { Stamp } from '../design/Stamp'
import { useLeaseLive } from '../ledger/useLedger'
import { formatDate, formatDayMonth, formatInstantDate, methodPhrase, ordinal, rupees } from '../lib/format'
import { useLive } from '../realtime/LiveProvider'
import { TenantDeposit } from '../deposit/TenantDeposit'
import { addonsQuery } from '../ledger/addons'
import { ConditionToConfirm, tenantState, useTenantReports } from '../condition/ConditionReports'
import { reportName } from '../condition/queries'
import { ReceiptDownload } from '../receipts/ReceiptDownload'
import styles from './SlipPage.module.css'
import { tenantHomeQuery } from './queries'
import { usePayment, type Payment } from './usePayment'

/** What the tenant needs first: what they owe and by when. Everything else sits below it. */
export function SlipPage() {
  const home = useQuery(tenantHomeQuery)
  // Both parties read the same ledger; a charge the landlord adds arrives here as it happens.
  useLeaseLive(home.data?.lease?.id ?? home.data?.movedOutOf?.id ?? null)

  if (home.isPending) return <SessionLoading />
  if (home.isError) {
    return <p role="alert">Couldn't load your home. Check your connection and reload.</p>
  }
  const { lease } = home.data
  if (!lease && home.data.deposit && home.data.movedOutOf) {
    const past = home.data.movedOutOf
    return (
      <div className={styles.layout}>
        <div className={styles.none}>
          <h1 className={styles.noneHeading}>Your tenancy has ended</h1>
          <p>
            You've moved out of {past.unit.label}, {past.property.name}. Your deposit is settled here, and your receipts
            stay yours.
          </p>
        </div>
        <ConditionToConfirm lease={past} />
        <TenantDeposit deposit={home.data.deposit} lease={past} />
      </div>
    )
  }
  if (!lease) {
    return (
      <div className={styles.none}>
        <h1 className={styles.noneHeading}>No active lease</h1>
        <p>When a landlord invites you to a home, open their link to see it here.</p>
      </div>
    )
  }
  return <Slip home={home.data} lease={lease} />
}

function Slip({ home, lease }: { home: TenantHome; lease: LeaseView }) {
  const queryClient = useQueryClient()
  const payment = usePayment(lease.id, home.pendingPayment)
  // The stamp lands only for a receipt that arrives while the tenant is looking at the slip.
  const [receiptOnArrival] = useState(home.lastReceipt?.id ?? null)

  useLive<PaymentEvent>(`/topic/leases/${lease.id}`, (event) => {
    if (event.type === 'payment.confirmed') {
      void queryClient.invalidateQueries({ queryKey: ['payment'] })
    }
  })

  const landing = home.lastReceipt !== null && home.lastReceipt.id !== receiptOnArrival
  return (
    <div className={styles.layout}>
      {home.outstandingPaise > 0 ? (
        <OwedSlip
          lines={home.outstanding}
          total={home.outstandingPaise}
          overdue={home.overdue}
          landlord={lease.landlord.fullName}
          payOnline={home.payOnline}
          payment={payment}
        />
      ) : (
        <PaidSlip home={home} landing={landing} />
      )}
      <ConditionToConfirm lease={lease} />
      <YourHome lease={lease} flatmates={home.flatmates} />
      {home.deposit && <TenantDeposit deposit={home.deposit} lease={lease} />}
    </div>
  )
}

/**
 * Everything owed, as one bill. Canary only when something is due today or late: charges that are
 * not due yet sit on a white slip, so the duplicate's yellow keeps meaning "now".
 */
function OwedSlip({ lines, total, overdue, landlord, payOnline, payment }: {
  lines: ChargeLine[]
  total: number
  overdue: boolean
  landlord: string
  payOnline: boolean
  payment: Payment
}) {
  const first = lines[0]
  const dueNow = lines.some((line) => line.status === 'DUE' || line.status === 'OVERDUE')
  const oneDate = lines.every((line) => line.dueOn === first.dueOn)
  const heading = lines.length === 1 ? first.description : !oneDate ? 'Outstanding' : dueNow ? 'Due now' : 'Coming up'

  return (
    <Sheet tone={dueNow ? 'duplicate' : 'original'} className={styles.slip} aria-labelledby="slip-heading">
      <h1 id="slip-heading" className={styles.slipHeading}>
        {heading}
      </h1>
      <p className={`${styles.amount} entry num`}>{rupees(total)}</p>
      <p className={styles.due}>
        {overdue ? (
          <Mark tone="overdue">Overdue since {formatDayMonth(first.dueOn)}</Mark>
        ) : (
          <>
            {oneDate ? 'Due' : 'First due'} <span className="entry">{formatDate(first.dueOn)}</span>
          </>
        )}
      </p>
      {lines.length > 1 && (
        <ul className={styles.lines} aria-label="What makes up this amount">
          {lines.map((line) => (
            <li key={line.id}>
              <span className={styles.lineFor}>
                {line.description}
                <span className={styles.lineDue}>
                  {line.status === 'OVERDUE' ? 'overdue since ' : 'due '}
                  {formatDayMonth(line.dueOn)}
                </span>
              </span>
              <span className="entry num">{rupees(line.amountPaise)}</span>
            </li>
          ))}
        </ul>
      )}
      <div className={styles.pay}>
        {!payment.waiting && (
          <Button
            block
            disabled={!payOnline}
            busy={payment.opening}
            aria-describedby="pay-note"
            onClick={() => void payment.start(lines.map((line) => line.id))}
          >
            Pay {rupees(total)}
          </Button>
        )}
        {payment.error && (
          <p role="alert" className={styles.payError}>
            {payment.error}
          </p>
        )}
        {/* One status line throughout, so the change to waiting is announced as it happens. */}
        <p id="pay-note" role="status" className={payment.waiting ? styles.waiting : styles.payNote}>
          {payment.waiting ? (
            <>
              Payment sent. Waiting for the bank to confirm{' '}
              <span className="entry num">{rupees(payment.waiting.amountPaise)}</span>. This slip changes by itself
              when it does.
            </>
          ) : payOnline ? (
            'Through Razorpay, in test mode: no real money moves. Your receipt comes once the bank confirms.'
          ) : (
            `Online payment isn't switched on for this home yet. Until then, pay ${firstName(landlord)} the way you do now.`
          )}
        </p>
      </div>
    </Sheet>
  )
}

/** Nothing owed. When a payment settled it, the stamp shows who confirmed it: Razorpay, or the landlord. */
function PaidSlip({ home, landing }: { home: TenantHome; landing: boolean }) {
  const receipt = home.lastReceipt
  const next = home.nextDueOn ? (
    <>
      Next rent, <span className="entry num">{rupees(home.nextRentPaise)}</span>, is due on{' '}
      <span className="entry">{formatDate(home.nextDueOn)}</span>.
    </>
  ) : (
    'No more rent is due on this lease.'
  )

  if (!receipt) {
    return (
      <Sheet className={styles.slip} aria-labelledby="slip-heading">
        <h1 id="slip-heading" className={styles.paidHeading}>
          All paid up
        </h1>
        <p className={styles.due}>{next}</p>
      </Sheet>
    )
  }
  return (
    <Sheet className={styles.slip} aria-labelledby="slip-heading">
      <h1 id="slip-heading" className={styles.slipHeading}>
        All paid up
      </h1>
      {/* The stamp is pressed across the amount it settles, as on a paper receipt. */}
      <div className={styles.stamped}>
        <p className={`${styles.amount} entry num`}>{rupees(receipt.amountPaise)}</p>
        <span className={styles.stampAt}>
          <Stamp size="slip" landing={landing}>
            Paid
          </Stamp>
        </span>
      </div>
      <p className={styles.due}>
        {receipt.method === 'RAZORPAY' || !receipt.receivedOn ? (
          <>
            Received on <span className="entry">{formatInstantDate(receipt.issuedAt)}</span>, confirmed by Razorpay.
          </>
        ) : (
          <>
            Paid {methodPhrase(receipt.method)} on <span className="entry">{formatDate(receipt.receivedOn)}</span>,
            recorded by your landlord.
          </>
        )}
      </p>
      <p>{next}</p>
      <div className={styles.receipt}>
        <ReceiptDownload receipt={receipt} variant="secondary">
          Download receipt {receipt.number}
        </ReceiptDownload>
      </div>
    </Sheet>
  )
}

function YourHome({ lease, flatmates }: { lease: LeaseView; flatmates: TenantHome['flatmates'] }) {
  const unit = lease.unit.roomLabel ? `${lease.unit.label}, ${lease.unit.roomLabel}` : lease.unit.label
  const addons = useQuery(addonsQuery(lease.id))
  const monthly = addons.data?.filter((addon) => addon.running) ?? []
  const reports = useTenantReports(lease.id)
  return (
    <Sheet className={styles.home} aria-labelledby="home-heading">
      <h2 id="home-heading" className={styles.homeHeading}>
        Your home
      </h2>
      <dl className={styles.list}>
        <div>
          <dt>Where</dt>
          <dd className="entry">
            {unit}, {lease.property.name}, {lease.property.city}
          </dd>
        </div>
        <div>
          <dt>Landlord</dt>
          <dd>
            <span className="entry">{lease.landlord.fullName}</span>
            <span className={styles.contact}>
              {lease.landlord.phone && (
                <a href={`tel:${lease.landlord.phone}`}>
                  <Icon name="phone" size={18} />
                  Call
                </a>
              )}
              <a href={`mailto:${lease.landlord.email}`}>
                <Icon name="mail" size={18} />
                Email
              </a>
            </span>
          </dd>
        </div>
        <div>
          <dt>Rent</dt>
          <dd className="entry num">
            {rupees(lease.rentPaise)} a month, due on the {ordinal(lease.dueDay)}
          </dd>
        </div>
        {flatmates.length > 0 && (
          <div>
            <dt>Shared with</dt>
            <dd>
              <ul className={styles.entries}>
                {flatmates.map((flatmate) => (
                  <li key={flatmate.name}>
                    <span className="entry">{flatmate.name}</span>
                    <span className="entry num">{rupees(flatmate.rentPaise)}</span>
                    <FlatmateMonth rent={flatmate.thisMonth} />
                  </li>
                ))}
              </ul>
            </dd>
          </div>
        )}
        {monthly.length > 0 && (
          <div>
            <dt>Also every month</dt>
            <dd className="entry num">
              {monthly.map((addon) => `${addon.label} ${rupees(addon.amountPaise)}`).join(', ')}
            </dd>
          </div>
        )}
        <div>
          <dt>Deposit</dt>
          <dd className="entry num">{lease.depositPaise > 0 ? rupees(lease.depositPaise) : 'None'}</dd>
        </div>
        <div>
          <dt>Moved in</dt>
          <dd className="entry">{formatDate(lease.startsOn)}</dd>
        </div>
        {reports.length > 0 && (
          <div>
            <dt>Condition</dt>
            <dd>
              <ul className={styles.entries}>
                {reports.map((report) => (
                  <li key={report.id}>
                    <Link to={`/t/condition/${report.id}`}>{reportName(report.kind)}</Link>
                    <span className={styles.contactNote}>{tenantState(report)}</span>
                  </li>
                ))}
              </ul>
            </dd>
          </div>
        )}
        <div>
          <dt>Lease ends</dt>
          <dd className="entry">{lease.endsOn ? formatDate(lease.endsOn) : 'No end date set'}</dd>
        </div>
      </dl>
      {lease.status === 'NOTICE' && lease.endsOn && (
        <p className={styles.notice}>This lease is on notice and ends on {formatDate(lease.endsOn)}.</p>
      )}
    </Sheet>
  )
}

/** A flatmate's month at a glance: whether their share is paid, never what else they owe. */
function FlatmateMonth({ rent }: { rent: MonthRent | null }) {
  if (!rent) return null
  switch (rent.status) {
    case 'PAID':
      return <Stamp>Paid</Stamp>
    case 'UPCOMING':
      return <Mark tone="upcoming">Due {formatDayMonth(rent.dueOn)}</Mark>
    case 'DUE':
      return <Mark tone="due">Due today</Mark>
    case 'OVERDUE':
      return <Mark tone="overdue">Overdue</Mark>
    case 'WAIVED':
      return <Mark tone="waived">Waived</Mark>
  }
}

function firstName(fullName: string) {
  return fullName.trim().split(/\s+/)[0] ?? fullName
}
