import { api } from '../api/client'
import type { VaultEntry } from '../api/types'

export function vaultQuery(leaseId: string) {
  return { queryKey: ['vault', leaseId], queryFn: () => api<VaultEntry[]>(`/leases/${leaseId}/documents`) }
}

/** Asks for a short-lived link and lets the browser download from storage; the page stays where it is. */
export async function downloadDocument(documentId: string) {
  const link = await api<{ url: string }>(`/documents/${documentId}/download?attachment=true`)
  window.location.assign(link.url)
}
