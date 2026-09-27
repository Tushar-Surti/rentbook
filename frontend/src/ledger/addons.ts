import { api } from '../api/client'
import type { Addon } from '../api/types'

/** A lease's monthly add-ons, read by both parties. */
export function addonsQuery(leaseId: string) {
  return { queryKey: ['addons', leaseId], queryFn: () => api<Addon[]>(`/leases/${leaseId}/addons`) }
}
