import { api, apiBlob } from '../api/client'
import type { Receipt } from '../api/types'

export function receiptsQuery(leaseId: string) {
  return { queryKey: ['receipts', leaseId], queryFn: () => api<Receipt[]>(`/leases/${leaseId}/receipts`) }
}

/** Fetches the PDF with the reader's session, then hands it to the browser to save. */
export async function saveReceipt(receipt: Receipt) {
  const blob = await apiBlob(`/receipts/${receipt.id}/pdf`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `rentbook-receipt-${receipt.number}.pdf`
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}
