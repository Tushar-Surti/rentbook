import { api } from '../api/client'
import type { TicketThread, TicketView } from '../api/types'

/** A landlord's requests (one property's, or only the open ones), or a tenant's own. */
/** {@code base} is "" for landlords and tenants, "/caretaker" for a caretaker's narrower door. */
export function ticketsQuery(filters: { propertyId?: string; open?: boolean } = {}, base = '') {
  const params = new URLSearchParams()
  if (filters.propertyId) params.set('propertyId', filters.propertyId)
  if (filters.open) params.set('open', 'true')
  const query = params.toString()
  return {
    queryKey: ['tickets', filters],
    queryFn: () => api<TicketView[]>(`${base}/tickets${query ? `?${query}` : ''}`),
  }
}

/** Photo links in a thread last five minutes, so the thread is refetched whenever it is opened again. */
export function threadQuery(ticketId: string, base = '') {
  return { queryKey: ['ticket', ticketId], queryFn: () => api<TicketThread>(`${base}/tickets/${ticketId}`) }
}
