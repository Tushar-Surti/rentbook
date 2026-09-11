import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, ApiError } from '../api/client'
import type { Checkout, PaymentView, PendingPayment } from '../api/types'
import { payWithRazorpay } from '../lib/razorpay'

export type Payment = ReturnType<typeof usePayment>

/**
 * Pays through Razorpay Checkout. Razorpay's success in the browser only moves the payment to waiting:
 * nothing counts as paid until Razorpay's signed webhook reaches the server, which the live channel
 * reports at once. A slow poll covers a dropped connection.
 */
export function usePayment(leaseId: string, pending: PendingPayment | null) {
  const queryClient = useQueryClient()
  const [sent, setSent] = useState<PendingPayment | null>(pending)
  const [opening, setOpening] = useState(false)
  const [error, setError] = useState<string>()

  const status = useQuery({
    queryKey: ['payment', sent?.id],
    queryFn: async () => {
      const view = await api<PaymentView>(`/payments/${sent?.id}`)
      if (view.status === 'CAPTURED') {
        void queryClient.invalidateQueries({ queryKey: ['tenant-home'] })
      }
      return view
    },
    enabled: Boolean(sent),
    refetchInterval: (query) => (isSettled(query.state.data) ? false : 5_000),
  })

  const settled = isSettled(status.data)
  const waiting = sent && !settled ? sent : null
  const declined = sent && status.data?.status === 'FAILED' ? declinedMessage(status.data.failureReason) : undefined

  const start = async (chargeIds: string[]) => {
    setError(undefined)
    setSent(null)
    setOpening(true)
    try {
      const checkout = await api<Checkout>('/payments/checkout', { method: 'POST', json: { leaseId, chargeIds } })
      const result = await payWithRazorpay(checkout)
      if (result.kind === 'dismissed') {
        if (result.lastFailure) setError(`Razorpay couldn't take the payment: ${result.lastFailure}`)
        return
      }
      try {
        await api(`/payments/${checkout.paymentId}/client-callback`, {
          method: 'POST',
          json: {
            razorpayOrderId: result.response.razorpay_order_id,
            razorpayPaymentId: result.response.razorpay_payment_id,
            razorpaySignature: result.response.razorpay_signature,
          },
        })
      } catch {
        // Razorpay's webhook confirms the payment whether or not this call arrives, so wait regardless.
      }
      setSent({ id: checkout.paymentId, amountPaise: checkout.amountPaise })
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : "Couldn't reach Razorpay. Check your connection and try again.")
    } finally {
      setOpening(false)
    }
  }

  return { start, opening, waiting, error: error ?? declined }
}

function isSettled(view?: PaymentView) {
  return view?.status === 'CAPTURED' || view?.status === 'FAILED'
}

function declinedMessage(reason: string | null) {
  return reason
    ? `The payment didn't go through: ${reason} Try again, or pay another way.`
    : "The payment didn't go through. Try again, or pay another way."
}
