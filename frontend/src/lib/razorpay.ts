import type { Checkout } from '../api/types'

const CHECKOUT_SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js'

type Success = { razorpay_payment_id: string; razorpay_order_id: string; razorpay_signature: string }

type Options = {
  key: string
  amount: number
  currency: string
  order_id: string
  name: string
  description: string
  prefill: { name?: string; email?: string; contact?: string }
  theme: { color: string }
  handler: (response: Success) => void
  modal: { ondismiss: () => void; confirm_close: boolean }
}

type Instance = {
  open(): void
  on(event: 'payment.failed', listener: (response: { error?: { description?: string } }) => void): void
}

declare global {
  interface Window {
    Razorpay?: new (options: Options) => Instance
  }
}

let loading: Promise<void> | null = null

/** Razorpay Checkout's script, fetched the first time someone pays rather than on every visit. */
function loadCheckout(): Promise<void> {
  if (window.Razorpay) return Promise.resolve()
  loading ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = CHECKOUT_SCRIPT
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      script.remove()
      loading = null
      reject(new Error('Razorpay Checkout did not load'))
    }
    document.head.append(script)
  })
  return loading
}

export type CheckoutResult = { kind: 'succeeded'; response: Success } | { kind: 'dismissed'; lastFailure?: string }

/**
 * Opens Razorpay Checkout for an order the server created. A failed attempt keeps the window open for
 * another try, so only a success or closing the window settles it.
 */
export async function payWithRazorpay(checkout: Checkout): Promise<CheckoutResult> {
  await loadCheckout()
  const Razorpay = window.Razorpay
  if (!Razorpay) throw new Error('Razorpay Checkout did not load')
  return new Promise((resolve) => {
    let lastFailure: string | undefined
    const instance = new Razorpay({
      key: checkout.keyId,
      amount: checkout.amountPaise,
      currency: checkout.currency,
      order_id: checkout.orderId,
      name: 'Rentbook',
      description: checkout.description,
      prefill: {
        name: checkout.prefill.name,
        email: checkout.prefill.email,
        contact: checkout.prefill.contact ?? undefined,
      },
      theme: { color: '#2a3190' },
      handler: (response) => resolve({ kind: 'succeeded', response }),
      modal: { ondismiss: () => resolve({ kind: 'dismissed', lastFailure }), confirm_close: true },
    })
    instance.on('payment.failed', (response) => {
      lastFailure = response.error?.description
    })
    instance.open()
  })
}
