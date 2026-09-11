import type { TicketStatus } from '../api/types'
import { Mark, type MarkTone } from '../design/Mark'
import { STATUS_LABELS } from './labels'

const TONES: Record<TicketStatus, MarkTone> = {
  OPEN: 'working',
  ACKNOWLEDGED: 'working',
  IN_PROGRESS: 'working',
  RESOLVED: 'done',
  CLOSED: 'done',
}

/** A request's status as a plain mark. Resolved is the landlord's word, not a verified fact, so it's never a stamp. */
export function TicketStatusMark({ status }: { status: TicketStatus }) {
  return <Mark tone={TONES[status]}>{STATUS_LABELS[status]}</Mark>
}
