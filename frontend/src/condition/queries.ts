import { api } from '../api/client'
import type { Condition, ConditionKind, ConditionReport, ConditionSummary } from '../api/types'

export function conditionReportsQuery(leaseId: string) {
  return {
    queryKey: ['conditions', leaseId],
    queryFn: () => api<ConditionSummary[]>(`/leases/${leaseId}/condition-reports`),
  }
}

export function conditionReportQuery(reportId: string) {
  return { queryKey: ['condition', reportId], queryFn: () => api<ConditionReport>(`/condition-reports/${reportId}`) }
}

export const CONDITIONS: { value: Condition; label: string }[] = [
  { value: 'GOOD', label: 'Good' },
  { value: 'WORN', label: 'Worn' },
  { value: 'DAMAGED', label: 'Damaged' },
  { value: 'MISSING', label: 'Missing' },
]

export const conditionLabel = (condition: Condition) =>
  CONDITIONS.find((option) => option.value === condition)?.label ?? condition

export const reportName = (kind: ConditionKind) => (kind === 'MOVE_IN' ? 'Move-in report' : 'Move-out report')
