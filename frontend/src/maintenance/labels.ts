import type { TicketCategory, TicketPriority, TicketPush, TicketStatus } from '../api/types'

export const CATEGORY_LABELS: Record<TicketCategory, string> = {
  PLUMBING: 'Plumbing, taps or drains',
  ELECTRICAL: 'Wiring, lights or switches',
  APPLIANCE: 'An appliance',
  FURNITURE: 'Furniture, doors or windows',
  CLEANING: 'Cleaning',
  PESTS: 'Pests',
  INTERNET: 'Wi-Fi or internet',
  OTHER: 'Something else',
}

export const PRIORITY_LABELS: Record<TicketPriority, string> = {
  LOW: 'When you can',
  NORMAL: 'This week',
  URGENT: 'Urgent: water, power or safety',
}

export const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: 'Open',
  ACKNOWLEDGED: 'Seen',
  IN_PROGRESS: 'Being fixed',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
}

/** What a button that moves a request to this status says. */
export const MOVE_LABELS: Record<TicketStatus, string> = {
  OPEN: 'Reopen',
  ACKNOWLEDGED: 'Mark as seen',
  IN_PROGRESS: 'Mark being fixed',
  RESOLVED: 'Mark resolved',
  CLOSED: 'Close request',
}

export function firstName(fullName: string): string {
  return fullName.trim().split(/\s+/)[0] ?? fullName
}

/** A change of status as a sentence: "Lata marked it resolved." */
export function statusSentence(actor: string, to: TicketStatus, subject = 'it'): string {
  const who = firstName(actor)
  switch (to) {
    case 'OPEN':
      return `${who} reopened ${subject}.`
    case 'ACKNOWLEDGED':
      return `${who} has seen ${subject}.`
    case 'IN_PROGRESS':
      return `${who} is having ${subject} fixed.`
    case 'RESOLVED':
      return `${who} marked ${subject} resolved.`
    case 'CLOSED':
      return `${who} closed ${subject}.`
  }
}

/** The live line for a change the other reader made to a request. */
export function requestLiveLine(type: string, push: TicketPush): string {
  const title = `"${push.title}"`
  if (type === 'ticket.opened') return `${firstName(push.actor)} reported ${title}.`
  if (type === 'ticket.message') return `${firstName(push.actor)} wrote on ${title}.`
  return statusSentence(push.actor, push.status, title)
}
