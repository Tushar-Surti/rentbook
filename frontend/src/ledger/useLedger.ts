import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Ledger } from '../api/types'
import { useLive } from '../realtime/LiveProvider'

export function ledgerQuery(leaseId: string, base = '') {
  return { queryKey: ['ledger', leaseId], queryFn: () => api<Ledger>(`${base}/leases/${leaseId}/ledger`) }
}

/** The lease's ledger, kept current: a change either party makes arrives over the live channel. */
export function useLedger(leaseId: string | null, base = '') {
  const query = useQuery({ ...ledgerQuery(leaseId ?? '', base), enabled: Boolean(leaseId) })
  useLeaseLive(leaseId)
  return query
}

/** Refreshes everything read from a lease whenever its ledger changes. */
export function useLeaseLive(leaseId: string | null) {
  const queryClient = useQueryClient()
  useLive(leaseId ? `/topic/leases/${leaseId}` : null, () => {
    void queryClient.invalidateQueries({ queryKey: ['ledger', leaseId] })
    void queryClient.invalidateQueries({ queryKey: ['receipts', leaseId] })
    void queryClient.invalidateQueries({ queryKey: ['vault', leaseId] })
    void queryClient.invalidateQueries({ queryKey: ['deposit', leaseId] })
    void queryClient.invalidateQueries({ queryKey: ['addons', leaseId] })
    void queryClient.invalidateQueries({ queryKey: ['conditions', leaseId] })
    void queryClient.invalidateQueries({ queryKey: ['condition'] })
    void queryClient.invalidateQueries({ queryKey: ['tenant-home'] })
    void queryClient.invalidateQueries({ queryKey: ['board'] })
  })
}
