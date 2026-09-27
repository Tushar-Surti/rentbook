import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, ApiError } from '../api/client'
import type { DepositStatus, Hook, IssuedInvite, Property, PropertyBoard, Unit } from '../api/types'
import { SessionLoading } from '../app/guards'
import { Button, LinkButton } from '../design/Button'
import { Mark } from '../design/Mark'
import { Stamp } from '../design/Stamp'
import { formatDayMonth, formatInstantShort, monthLabel, ordinal, rupees } from '../lib/format'
import { ticketsQuery } from '../maintenance/queries'
import { RequestList } from '../maintenance/RequestList'
import { AddUnitForm } from './AddUnitForm'
import styles from './BookPage.module.css'
import { boardQuery, propertyQuery } from './queries'
import { ShareLink } from './ShareLink'

const KIND_NAMES: Record<Property['kind'], string> = { PG: 'PG', APARTMENT: 'Apartment building', HOUSE: 'House' }

const DEPOSIT_STATE: Record<DepositStatus | 'NONE', string> = {
  NONE: 'To settle',
  PROPOSED: 'Sent, waiting',
  QUERIED: 'Has a question',
  ACCEPTED: 'Agreed, refund to record',
  SETTLED: 'Settled',
}

/** One property's page in the book: the month's register of every unit it lets, and what to add next. */
export function BookPage() {
  const { propertyId = '' } = useParams()
  const board = useQuery(boardQuery)
  const property = useQuery(propertyQuery(propertyId))
  const requests = useQuery(ticketsQuery({ propertyId, open: true }))
  const [issued, setIssued] = useState<IssuedInvite>()

  if (board.isPending || property.isPending) return <SessionLoading />
  if (property.isError) {
    return (
      <div className={styles.missing}>
        <h1 className={styles.title}>This property isn't in your book</h1>
        <Link to="/l">Open your book</Link>
      </div>
    )
  }

  const page = board.data?.properties.find((entry) => entry.id === propertyId)
  const units = new Map(property.data.units.map((unit) => [unit.id, unit]))
  const openRequests = requests.data?.length ?? 0
  const deposits = board.data?.deposits.filter((due) => due.propertyId === propertyId) ?? []

  return (
    <div className={styles.book}>
      <header className={styles.header}>
        <h1 className={styles.title}>{property.data.name}</h1>
        <p className={styles.address}>
          {KIND_NAMES[property.data.kind]}, {property.data.addressLine}, {property.data.city} {property.data.pincode}
        </p>
        {/* The section sits under the register; this line puts it in the first view, even on a phone. */}
        {openRequests > 0 && (
          <p className={styles.needsNote}>
            <a href="#needs-you">
              {openRequests} {openRequests === 1 ? 'request needs' : 'requests need'} you
            </a>
          </p>
        )}
      </header>

      {issued && (
        <section className={styles.issued} aria-labelledby="issued-heading">
          <h2 id="issued-heading" className={styles.sectionHeading}>
            New link for {issued.invite.tenantName}
          </h2>
          <ShareLink issued={issued} />
          <Button variant="quiet" onClick={() => setIssued(undefined)}>
            Done
          </Button>
        </section>
      )}

      <section aria-labelledby="register-heading">
        <h2 id="register-heading" className="visually-hidden">
          Units and tenants
        </h2>
        {!page || page.hooks.length === 0 ? (
          <div className={styles.empty}>
            <p>No units yet. Add the flats, rooms or beds you let out below.</p>
            <p>A PG room with beds is let bed by bed; add the room with its number of beds.</p>
          </div>
        ) : (
          <Register page={page} month={board.data?.month ?? ''} units={units} onIssued={setIssued} />
        )}
      </section>

      {deposits.length > 0 && (
        <section className={styles.needs} aria-labelledby="deposits-heading">
          <h2 id="deposits-heading" className={styles.sectionHeading}>
            Deposits to settle
          </h2>
          <ul className={styles.deposits}>
            {deposits.map((due) => (
              <li key={due.leaseId}>
                <Link to={`/l/p/${propertyId}/leases/${due.leaseId}`} className="entry">
                  {due.tenantName}
                </Link>
                <span className={styles.depositUnit}>
                  {due.roomLabel ? `${due.unitLabel}, ${due.roomLabel}` : due.unitLabel}
                  {due.lastDay && `, last day ${formatDayMonth(due.lastDay)}`}
                </span>
                <span className="entry num">{rupees(due.heldPaise)}</span>
                <span className={styles.depositState}>{DEPOSIT_STATE[due.status ?? 'NONE']}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {requests.data && requests.data.length > 0 && (
        <section id="needs-you" className={styles.needs} aria-labelledby="needs-heading">
          <h2 id="needs-heading" className={styles.sectionHeading}>
            Needs you
          </h2>
          <RequestList
            // Urgent first; otherwise the most recently active first, as they arrive.
            tickets={[...requests.data].sort(
              (a, b) => Number(b.priority === 'URGENT') - Number(a.priority === 'URGENT'),
            )}
            linkTo={(ticket) => `/l/p/${propertyId}/requests/${ticket.id}`}
            showTenant
            label={`Open requests at ${property.data.name}`}
          />
        </section>
      )}

      <section className={styles.add} aria-labelledby="add-heading">
        <h2 id="add-heading" className={styles.sectionHeading}>
          Add to {property.data.name}
        </h2>
        <AddUnitForm property={property.data} />
      </section>
    </div>
  )
}

/**
 * The month's register of one property. {@code base} is the book it sits in ("/l" for the landlord, "/c" for
 * a caretaker); {@code readOnly} leaves out inviting, which is the landlord's alone.
 */
export function Register({ page, month, units, onIssued, base = '/l', readOnly = false }: {
  page: PropertyBoard
  month: string
  units: Map<string, Unit>
  onIssued?: (issued: IssuedInvite) => void
  base?: string
  readOnly?: boolean
}) {
  // Natural order, so Room 9 comes before Room 10 and every room's beds read A, B, C.
  const byLabel = (a: { label: string }, b: { label: string }) =>
    a.label.localeCompare(b.label, 'en-IN', { numeric: true })
  const standalone = page.hooks.filter((hook) => !hook.roomId).sort(byLabel)
  const grouped = new Map<string, { label: string; beds: Hook[] }>()
  for (const hook of page.hooks) {
    if (!hook.roomId) continue
    const room = grouped.get(hook.roomId) ?? { label: hook.roomLabel ?? '', beds: [] }
    room.beds.push(hook)
    grouped.set(hook.roomId, room)
  }
  grouped.forEach((room) => room.beds.sort(byLabel))
  const rooms = new Map([...grouped.entries()].sort(([, a], [, b]) => byLabel(a, b)))

  const occupied = page.hooks.filter((hook) => hook.occupant)
  const vacant = page.hooks.filter((hook) => !hook.occupant && !hook.invite && hook.status === 'VACANT').length
  const waiting = page.hooks.filter((hook) => hook.invite).length
  const billed = occupied.map((hook) => hook.occupant?.thisMonth).filter((rent) => rent && rent.status !== 'WAIVED')
  const expected = billed.reduce((sum, rent) => sum + (rent?.amountPaise ?? 0), 0)
  const collected = billed.reduce((sum, rent) => sum + (rent?.status === 'PAID' ? rent.amountPaise : 0), 0)
  const monthName = month ? monthLabel(month) : 'this month'
  const row = (hook: Hook) => (
    <HookRow
      key={hook.unitId}
      hook={hook}
      unit={units.get(hook.unitId)}
      propertyId={page.id}
      onIssued={onIssued ?? (() => undefined)}
      base={base}
      readOnly={readOnly}
    />
  )

  return (
    <table className={styles.register}>
      <colgroup>
        <col className={styles.colUnit} />
        <col />
        <col className={styles.colRent} />
        <col className={styles.colDue} />
        <col className={styles.colAction} />
      </colgroup>
      <thead>
        <tr>
          <th scope="col">Unit</th>
          <th scope="col">Tenant</th>
          <th scope="col" className={styles.amount}>
            Rent
          </th>
          <th scope="col">{monthName}</th>
          <th scope="col">
            <span className="visually-hidden">Actions</span>
          </th>
        </tr>
      </thead>
      {standalone.length > 0 && <tbody>{standalone.map(row)}</tbody>}
      {[...rooms.entries()].map(([roomId, room]) => (
        <tbody key={roomId} className={styles.room}>
          <tr className={styles.roomRow}>
            <th scope="colgroup" colSpan={5}>
              {room.label}
              <span className={styles.roomCount}>
                {room.beds.length} {room.beds.length === 1 ? 'bed' : 'beds'}
              </span>
            </th>
          </tr>
          {room.beds.map(row)}
        </tbody>
      ))}
      <tfoot>
        <tr>
          <th scope="row" colSpan={2}>
            Collected in {monthName}
            <span className={styles.tally}>
              {occupied.length} of {page.hooks.length} let, {vacant} vacant
              {waiting > 0 ? `, ${waiting} ${waiting === 1 ? 'invite' : 'invites'} waiting` : ''}
            </span>
          </th>
          <td className={`${styles.amount} ${styles.total}`}>
            <span className="entry num">{rupees(collected)}</span>
            <span className={styles.perMonth}> of {rupees(expected)}</span>
          </td>
          <td colSpan={2} />
        </tr>
      </tfoot>
    </table>
  )
}

function RentStatus({ occupant }: { occupant: NonNullable<Hook['occupant']> }) {
  const rent = occupant.thisMonth
  // The row flips live when Razorpay confirms the rent; the stamp lands only on a row that showed it unpaid.
  const [paidWhenShown] = useState(rent?.status === 'PAID')
  if (!rent) {
    return <span className="entry">{ordinal(occupant.dueDay)}</span>
  }
  switch (rent.status) {
    case 'UPCOMING':
      return <Mark tone="upcoming">Due {formatDayMonth(rent.dueOn)}</Mark>
    case 'DUE':
      return <Mark tone="due">Due today</Mark>
    case 'OVERDUE':
      return <Mark tone="overdue">Overdue</Mark>
    case 'PAID':
      return <Stamp landing={!paidWhenShown}>Paid</Stamp>
    case 'WAIVED':
      return <Mark tone="waived">Waived</Mark>
  }
}

function HookRow({ hook, unit, propertyId, onIssued, base, readOnly }: {
  hook: Hook
  unit?: Unit
  propertyId: string
  onIssued: (issued: IssuedInvite) => void
  base: string
  readOnly: boolean
}) {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['board'] })

  const resend = useMutation({
    mutationFn: (inviteId: string) => api<IssuedInvite>(`/invites/${inviteId}/resend`, { method: 'POST' }),
    onSuccess: (issued) => {
      onIssued(issued)
      void refresh()
    },
  })
  const revoke = useMutation({
    mutationFn: (inviteId: string) => api(`/invites/${inviteId}/revoke`, { method: 'POST' }),
    onSuccess: () => void refresh(),
  })
  const failure = resend.error ?? revoke.error
  const open = !readOnly && !hook.occupant && !hook.invite && hook.status === 'VACANT'

  return (
    <tr>
      <td className={styles.unitCell}>{hook.label}</td>
      <td className={styles.tenantCell}>
        {hook.occupant ? (
          <Link to={`${base}/p/${propertyId}/leases/${hook.occupant.leaseId}`} className="entry">
            {hook.occupant.tenantName}
          </Link>
        ) : hook.invite ? (
          <>
            <Mark tone="invited">Invited</Mark> <span className="entry">{hook.invite.tenantName}</span>
          </>
        ) : hook.status === 'INACTIVE' ? (
          <Mark tone="inactive">Not in use</Mark>
        ) : (
          <Mark tone="vacant">Vacant</Mark>
        )}
      </td>
      <td className={`${styles.amount} ${styles.rentCell}`}>
        {hook.occupant ? (
          <span className="entry num">{rupees(hook.occupant.rentPaise)}</span>
        ) : unit?.defaultRentPaise ? (
          <span className={styles.asking}>asking {rupees(unit.defaultRentPaise)}</span>
        ) : null}
      </td>
      <td className={styles.dueCell}>
        {hook.occupant ? (
          <RentStatus occupant={hook.occupant} />
        ) : hook.invite ? (
          <span className={styles.asking}>link expires {formatInstantShort(hook.invite.expiresAt)}</span>
        ) : null}
      </td>
      <td className={styles.actionCell}>
        {open && (
          <span className={styles.inviteActions}>
            <LinkButton to={`/l/p/${propertyId}/units/${hook.unitId}/invite`} variant="quiet" className={styles.rowAction}>
              Invite a tenant
            </LinkButton>
            {hook.listingId ? (
              <LinkButton to={`/l/listings/${hook.listingId}`} variant="quiet" className={styles.rowAction}>
                Listed
              </LinkButton>
            ) : (
              <LinkButton to={`/l/p/${propertyId}/units/${hook.unitId}/list`} variant="quiet" className={styles.rowAction}>
                List it
              </LinkButton>
            )}
          </span>
        )}
        {hook.invite && !confirming && !readOnly && (
          <span className={styles.inviteActions}>
            <Button
              variant="quiet"
              className={styles.rowAction}
              busy={resend.isPending}
              onClick={() => resend.mutate(hook.invite!.inviteId)}
            >
              New link
            </Button>
            <Button variant="quiet" className={styles.rowAction} onClick={() => setConfirming(true)}>
              Withdraw
            </Button>
          </span>
        )}
        {hook.invite && confirming && (
          <span className={styles.inviteActions}>
            <span>Withdraw {hook.invite.tenantName}'s invite?</span>
            <Button
              variant="quiet"
              className={styles.rowAction}
              busy={revoke.isPending}
              onClick={() => revoke.mutate(hook.invite!.inviteId)}
            >
              Withdraw
            </Button>
            <Button variant="quiet" className={styles.rowAction} onClick={() => setConfirming(false)}>
              Keep it
            </Button>
          </span>
        )}
        {failure && (
          <span role="alert" className={styles.rowError}>
            {failure instanceof ApiError ? failure.message : 'That did not go through. Try again.'}
          </span>
        )}
      </td>
    </tr>
  )
}
