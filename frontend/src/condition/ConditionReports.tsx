import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, ApiError } from '../api/client'
import type { ConditionKind, ConditionReport, ConditionSummary, LeaseView } from '../api/types'
import { Button, LinkButton } from '../design/Button'
import { FormError } from '../design/Field'
import { Sheet } from '../design/Sheet'
import { formatInstantDate } from '../lib/format'
import styles from './ConditionReports.module.css'
import { conditionReportsQuery, reportName } from './queries'

const firstName = (fullName: string) => fullName.split(' ')[0]

/** The landlord's list of a lease's condition reports, and where the next one starts. */
export function LeaseConditionReports({ lease }: { lease: LeaseView }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const reports = useQuery(conditionReportsQuery(lease.id))
  const [busy, setBusy] = useState<ConditionKind>()
  const [formError, setFormError] = useState<string>()
  const tenant = firstName(lease.tenant.fullName)
  const unit = lease.unit.roomLabel ? `${lease.unit.label}, ${lease.unit.roomLabel}` : lease.unit.label
  const base = `/l/p/${lease.property.id}/leases/${lease.id}/condition`

  if (!reports.data) return null
  const has = (kind: ConditionKind) => reports.data.some((report) => report.kind === kind)
  const canStartMoveIn = !has('MOVE_IN') && lease.status !== 'ENDED'
  const canStartMoveOut = !has('MOVE_OUT') && lease.status !== 'ACTIVE'

  const start = async (kind: ConditionKind) => {
    setBusy(kind)
    setFormError(undefined)
    try {
      const report = await api<ConditionReport>(`/leases/${lease.id}/condition-reports`, { method: 'POST', json: { kind } })
      queryClient.setQueryData(['condition', report.id], report)
      await queryClient.invalidateQueries({ queryKey: ['conditions', lease.id] })
      navigate(`${base}/${report.id}`)
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not reach Rentbook. Check your connection.')
    } finally {
      setBusy(undefined)
    }
  }

  return (
    <div className={styles.reports}>
      {reports.data.length === 0 && (
        <p className={styles.hint}>
          Walk through {unit} and note each room's condition, with photos. {tenant} confirms it, and at move-out the
          same lines show what changed, which is what any deposit deduction should rest on.
        </p>
      )}
      {reports.data.length > 0 && (
        <ul className={styles.list}>
          {reports.data.map((report) => (
            <li key={report.id} className={styles.row}>
              <Link to={`${base}/${report.id}`} className={styles.name}>
                {reportName(report.kind)}
              </Link>
              <span className={styles.state}>{landlordState(report, tenant)}</span>
              {report.kind === 'MOVE_OUT' && report.worse > 0 && (
                <span className={styles.worse}>{report.worse} worse than at move-in</span>
              )}
            </li>
          ))}
        </ul>
      )}
      <FormError>{formError}</FormError>
      {(canStartMoveIn || canStartMoveOut) && (
        <div className={styles.actions}>
          {canStartMoveIn && (
            <Button variant="secondary" busy={busy === 'MOVE_IN'} onClick={() => void start('MOVE_IN')}>
              Start the move-in report
            </Button>
          )}
          {canStartMoveOut && (
            <Button variant="secondary" busy={busy === 'MOVE_OUT'} onClick={() => void start('MOVE_OUT')}>
              Start the move-out report
            </Button>
          )}
        </div>
      )}
    </div>
  )
}

function landlordState(report: ConditionSummary, tenant: string) {
  switch (report.status) {
    case 'DRAFT':
      return `Draft, ${report.lines} lines`
    case 'SENT':
      return `Sent ${formatInstantDate(report.sentAt!)}, waiting for ${tenant}`
    case 'CONFIRMED':
      return `Confirmed by ${tenant} ${formatInstantDate(report.confirmedAt!)}`
  }
}

/** On the tenant's home: a report waiting for them to check and confirm. */
export function ConditionToConfirm({ lease }: { lease: LeaseView }) {
  const reports = useQuery(conditionReportsQuery(lease.id))
  const waiting = reports.data?.filter((report) => report.status === 'SENT') ?? []
  if (waiting.length === 0) return null
  const landlord = firstName(lease.landlord.fullName)
  return (
    <>
      {waiting.map((report) => (
        <Sheet key={report.id} className={styles.waiting} aria-labelledby={`confirm-${report.id}`}>
          <h2 id={`confirm-${report.id}`} className={styles.waitingHeading}>
            Check your {report.kind === 'MOVE_IN' ? 'move-in' : 'move-out'} report
          </h2>
          <p>
            {landlord} wrote up the condition of your home, room by room. Add a note or a photo wherever you see it
            differently, then confirm it.
          </p>
          <LinkButton to={`/t/condition/${report.id}`} variant="primary">
            Open the report
          </LinkButton>
        </Sheet>
      ))}
    </>
  )
}

/** The tenant's reports that have reached them, for the "Your home" record. */
export function useTenantReports(leaseId: string) {
  const reports = useQuery(conditionReportsQuery(leaseId))
  return (reports.data ?? []).filter((report) => report.status !== 'DRAFT')
}

export function tenantState(report: ConditionSummary) {
  return report.status === 'CONFIRMED'
    ? `confirmed ${formatInstantDate(report.confirmedAt!)}`
    : 'waiting for you to confirm'
}
