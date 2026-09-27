import { api } from '../api/client'
import type { CaretakerBoard, LeaseView } from '../api/types'

/** Everything a caretaker reads goes through their own, narrower door. */
export const CARETAKER_API = '/caretaker'

export const caretakerBoardQuery = {
  queryKey: ['caretaker-board'],
  queryFn: () => api<CaretakerBoard>(`${CARETAKER_API}/board`),
}

export function caretakerLeaseQuery(leaseId: string) {
  return { queryKey: ['lease', leaseId], queryFn: () => api<LeaseView>(`${CARETAKER_API}/leases/${leaseId}`) }
}
