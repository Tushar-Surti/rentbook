import AxeBuilder from '@axe-core/playwright'
import { expect, test, type BrowserContextOptions, type Page, type TestInfo } from '@playwright/test'
import { createHmac } from 'node:crypto'
import { deflateSync } from 'node:zlib'

// Set SHOTS_DIR to keep a full-page screenshot of every screen the journey passes through.
const SHOTS_DIR = process.env.SHOTS_DIR
const PASSWORD = 'correct horse battery'

// The backend must run with the same stand-in Razorpay secrets (see README.md).
const RAZORPAY_KEY_SECRET = process.env.E2E_RAZORPAY_KEY_SECRET ?? 'e2e-key-secret'
const RAZORPAY_WEBHOOK_SECRET = process.env.E2E_RAZORPAY_WEBHOOK_SECRET ?? 'e2e-webhook-secret'

// Stands in for Razorpay Checkout: the payment succeeds at once, signed the way Razorpay signs it.
const FAKE_CHECKOUT = `
window.Razorpay = function (options) {
  this.on = function () {}
  this.open = function () {
    var paymentId = 'pay_e2e' + Math.random().toString(36).slice(2, 12)
    window.rentbookSign(options.order_id + '|' + paymentId).then(function (signature) {
      options.handler({ razorpay_payment_id: paymentId, razorpay_order_id: options.order_id, razorpay_signature: signature })
    })
  }
}`

function hmac(secret: string, message: string) {
  return createHmac('sha256', secret).update(message).digest('hex')
}

/** A small PNG to stand in for a phone photo of a leak: a 96px square shading from pipe grey to water blue. */
function photoOfALeak(): Buffer {
  const size = 96
  const rows = Buffer.alloc(size * (1 + size * 3))
  for (let y = 0; y < size; y++) {
    const row = y * (1 + size * 3)
    for (let x = 0; x < size; x++) {
      const t = (x + y) / (2 * size)
      rows[row + 1 + x * 3] = Math.round(120 - 80 * t)
      rows[row + 2 + x * 3] = Math.round(130 - 20 * t)
      rows[row + 3 + x * 3] = Math.round(140 + 90 * t)
    }
  }
  const crc = (bytes: Buffer) => {
    let value = 0xffffffff
    for (const byte of bytes) {
      value ^= byte
      for (let bit = 0; bit < 8; bit++) value = value & 1 ? (value >>> 1) ^ 0xedb88320 : value >>> 1
    }
    return (value ^ 0xffffffff) >>> 0
  }
  const chunk = (type: string, data: Buffer) => {
    const typed = Buffer.concat([Buffer.from(type, 'ascii'), data])
    const length = Buffer.alloc(4)
    length.writeUInt32BE(data.length)
    const sum = Buffer.alloc(4)
    sum.writeUInt32BE(crc(typed))
    return Buffer.concat([length, typed, sum])
  }
  const header = Buffer.alloc(13)
  header.writeUInt32BE(size, 0)
  header.writeUInt32BE(size, 4)
  header.set([8, 2, 0, 0, 0], 8)
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(rows)),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

/** Razorpay's order.paid webhook, as its servers would send it. */
function orderPaid(orderId: string, paymentId: string, amountPaise: number) {
  return JSON.stringify({
    entity: 'event',
    event: 'order.paid',
    contains: ['payment', 'order'],
    payload: {
      payment: {
        entity: { id: paymentId, entity: 'payment', amount: amountPaise, currency: 'INR', status: 'captured', order_id: orderId, method: 'upi' },
      },
      order: { entity: { id: orderId, entity: 'order', amount: amountPaise, amount_paid: amountPaise, status: 'paid' } },
    },
    created_at: Math.floor(Date.now() / 1000),
  })
}

async function capture(page: Page, testInfo: TestInfo, name: string) {
  if (SHOTS_DIR) {
    await page.screenshot({ path: `${SHOTS_DIR}/${testInfo.project.name}-${name}.png`, fullPage: true })
  }
}

async function expectAccessible(page: Page) {
  const { violations } = await new AxeBuilder({ page }).analyze()
  const serious = violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
  expect(serious.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target).join(', ')}`)).toEqual([])
}

test('a landlord invites a tenant to a PG bed, the tenant moves in, and both read one live rent book', async ({
  page,
  browser,
}, testInfo) => {
  const run = `${testInfo.project.name}-${Date.now().toString(36)}`
  const landlordEmail = `lata-${run}@example.in`
  const tenantEmail = `asha-${run}@example.in`

  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.keyboard.press('Tab')
  await page.keyboard.press('Tab')
  await capture(page, testInfo, 'login')
  await expectAccessible(page)

  // The landlord opens a rent book.
  await page.getByRole('link', { name: 'Create your account' }).click()
  await page.getByLabel('Your name').fill('Lata Iyer')
  await page.getByLabel('Email').fill(landlordEmail)
  await page.getByLabel('Password').fill(PASSWORD)
  await capture(page, testInfo, 'register')
  await page.getByRole('button', { name: 'Email me a code' }).click()

  // The dev profile's signup code is always 000000; a real one arrives by email.
  await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible()
  await capture(page, testInfo, 'register-code')
  await expectAccessible(page)
  await page.getByLabel('Code').fill('000000')
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page.getByRole('heading', { name: 'Add your first property' })).toBeVisible()
  await capture(page, testInfo, 'first-property')
  await expectAccessible(page)
  await page.getByLabel('Name').fill('Sunrise PG')
  await page.getByLabel('Street address').fill('14 Koramangala 5th Block')
  await page.getByLabel('City').fill('Bengaluru')
  await page.getByLabel('PIN code').fill('560095')
  await page.getByRole('button', { name: 'Add property' }).click()
  await expect(page.getByRole('heading', { name: 'Sunrise PG', level: 1 })).toBeVisible()
  await capture(page, testInfo, 'book-empty')

  // A PG room is added with its beds in one go.
  for (const [room, beds] of [['Room 201', '3'], ['Room 202', '2']] as const) {
    await page.getByLabel('Label').fill(room)
    await page.getByLabel('Beds in it').fill(beds)
    await page.getByLabel('Asking rent per bed').fill('12500')
    await page.getByRole('button', { name: 'Add', exact: true }).click()
    await expect(page.getByRole('status').filter({ hasText: `Added ${room} with ${beds} beds.` })).toBeVisible()
  }
  await expect(page.getByRole('link', { name: 'Invite a tenant' })).toHaveCount(5)
  await capture(page, testInfo, 'book-rooms')
  await expectAccessible(page)

  // Online rent: the landlord becomes a Razorpay Route linked account (the stand-in Razorpay here).
  await page.getByRole('link', { name: 'Payouts' }).click()
  await expect(page.getByRole('heading', { name: 'Get paid online' })).toBeVisible()
  await page.getByLabel('Name as on your PAN').fill('Lata Iyer')
  await page.getByLabel('PAN (optional)').fill('ABCPI1234K')
  await page.getByLabel('Mobile number').fill('9876543210')
  await page.getByLabel('Street address').fill('14 Koramangala 5th Block')
  await page.getByLabel('City').fill('Bengaluru')
  await page.getByLabel('PIN code').fill('560095')
  await page.getByLabel('State').fill('Karnataka')
  await page.getByLabel("Account holder's name").fill('Lata Iyer')
  await page.getByLabel('Bank account number').fill('50100123456789')
  await page.getByLabel('Account number again').fill('50100123456789')
  await page.getByLabel('IFSC').fill('HDFC0001234')
  await capture(page, testInfo, 'payouts-form')
  await expectAccessible(page)
  await page.getByRole('button', { name: 'Set up payouts' }).click()
  await expect(page.getByRole('heading', { name: 'Online rent is on' })).toBeVisible()
  await expect(page.getByText('6789')).toBeVisible()
  await capture(page, testInfo, 'payouts-on')
  await page.getByRole('link', { name: 'Sunrise PG' }).click()

  // The invite: what the landlord writes appears on the duplicate the tenant will receive.
  await page.getByRole('link', { name: 'Invite a tenant' }).first().click()
  await expect(page.getByRole('heading', { name: 'Invite a tenant to Bed A, Room 201' })).toBeVisible()
  await page.getByLabel("Tenant's name").fill('Asha Rao')
  await page.getByLabel("Tenant's email").fill(tenantEmail)
  await page.getByLabel("Tenant's mobile (optional)").fill('9812345678')
  await page.getByLabel('Deposit').fill('25000')
  // Rent falls due on the move-in day, so next month's rent is never already on the slip, whatever the date.
  const moveInDay = Math.min(Number(new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Kolkata' }).slice(8)), 28)
  await page.getByLabel('Rent due on').selectOption(String(moveInDay))
  await expect(page.getByRole('heading', { name: 'What Asha Rao will see' })).toBeVisible()
  await capture(page, testInfo, 'invite-form')
  await expectAccessible(page)
  await page.getByRole('button', { name: 'Send invite' }).click()

  await expect(page.getByRole('heading', { name: 'Invite sent to Asha Rao' })).toBeVisible()
  const link = await page.getByLabel('Invite link for Asha Rao').inputValue()
  await capture(page, testInfo, 'invite-sent')
  await page.getByRole('link', { name: 'Back to Sunrise PG' }).click()
  await expect(page.getByText('Invited', { exact: true })).toBeVisible()
  await capture(page, testInfo, 'book-invited')

  // The tenant opens the link on their own device. Moving in today puts the deposit and this month's rent on the book.
  const tenantContext = await browser.newContext(testInfo.project.use as BrowserContextOptions)
  const tenant = await tenantContext.newPage()
  await tenant.route('https://checkout.razorpay.com/v1/checkout.js', (route) =>
    route.fulfill({ contentType: 'text/javascript', body: FAKE_CHECKOUT }),
  )
  await tenant.exposeFunction('rentbookSign', (message: string) => hmac(RAZORPAY_KEY_SECRET, message))
  await tenant.goto(new URL(link).pathname)
  await expect(
    tenant.getByRole('heading', { name: 'Lata Iyer has invited you to rent Bed A, Room 201 at Sunrise PG' }),
  ).toBeVisible()
  await capture(tenant, testInfo, 'accept')
  await expectAccessible(tenant)
  await tenant.getByLabel('Choose a password').fill(PASSWORD)
  await tenant.getByRole('button', { name: 'Accept invite' }).click()

  await expect(tenant.getByRole('heading', { name: 'Due now' })).toBeVisible()
  await expect(tenant.getByText('₹37,500', { exact: true })).toBeVisible()
  await capture(tenant, testInfo, 'slip')
  await expectAccessible(tenant)

  // The landlord's page learns about the move-in without a reload.
  await expect(page.getByRole('status').filter({ hasText: 'Asha Rao accepted your invite and has moved in.' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Asha Rao' })).toBeVisible()
  await capture(page, testInfo, 'book-moved-in')

  // The landlord adds the month's electricity to Asha's lease...
  await page.getByRole('link', { name: 'Asha Rao' }).click()
  await expect(page.getByRole('heading', { name: 'Asha Rao', level: 1 })).toBeVisible()
  await page.getByLabel('Description').fill('Electricity for September')
  await page.getByLabel('Amount').fill('1800')
  await page.getByRole('button', { name: 'Add charge' }).click()
  await expect(page.getByRole('cell', { name: 'Electricity for September' })).toBeVisible()
  await capture(page, testInfo, 'lease')
  await expectAccessible(page)

  // ...and the tenant's slip changes on its own, saying who changed it: one rent book, two readers.
  await expect(tenant.getByText('₹39,300', { exact: true })).toBeVisible()
  await expect(tenant.getByRole('status').filter({ hasText: 'Lata added Electricity for September, ₹1,800.' })).toBeVisible()
  await capture(tenant, testInfo, 'slip-charges')
  await tenant.getByRole('link', { name: 'Rent', exact: true }).click()
  await expect(tenant.getByRole('cell', { name: 'Electricity for September' })).toBeVisible()
  await capture(tenant, testInfo, 'rent')
  await expectAccessible(tenant)

  // The landlord waives it after all. The line stays in the book, struck through, for both of them.
  const electricity = page.getByRole('row', { name: /Electricity for September/ })
  await electricity.getByRole('button', { name: 'Waive' }).click()
  await capture(page, testInfo, 'lease-waive-confirm')
  await electricity.getByRole('button', { name: 'Waive it' }).click()
  await expect(electricity.getByText('Waived')).toBeVisible()
  await capture(page, testInfo, 'lease-waived')
  await expect(tenant.getByRole('status').filter({ hasText: 'Lata waived Electricity for September.' })).toBeVisible()
  await expect(tenant.getByRole('row', { name: /Electricity for September/ }).getByText('Waived')).toBeVisible()
  await capture(tenant, testInfo, 'rent-waived')

  // Asha pays the deposit and September's rent through Razorpay. Checkout reports success at once,
  // but the browser's word isn't enough: the slip waits for the bank.
  await tenant.getByRole('link', { name: 'Home', exact: true }).click()
  await expect(tenant.getByRole('heading', { name: 'Due now' })).toBeVisible()
  const callback = tenant.waitForRequest((request) => request.url().endsWith('/client-callback'))
  await tenant.getByRole('button', { name: 'Pay ₹37,500' }).click()
  const sent = (await callback).postDataJSON() as { razorpayOrderId: string; razorpayPaymentId: string }
  await expect(tenant.getByRole('status').filter({ hasText: 'Waiting for the bank to confirm ₹37,500' })).toBeVisible()
  await expect(tenant.getByRole('heading', { name: 'Due now' })).toBeVisible()
  await capture(tenant, testInfo, 'slip-waiting')
  await expectAccessible(tenant)

  // Lata has her book open when Razorpay's signed webhook arrives. Only now is anything paid, on both screens.
  await page.getByRole('link', { name: 'Sunrise PG' }).click()
  await expect(page.getByRole('link', { name: 'Asha Rao' })).toBeVisible()
  const event = orderPaid(sent.razorpayOrderId, sent.razorpayPaymentId, 3_750_000)
  const delivered = await page.request.post('/api/v1/webhooks/razorpay', {
    data: event,
    headers: {
      'Content-Type': 'application/json',
      'X-Razorpay-Signature': hmac(RAZORPAY_WEBHOOK_SECRET, event),
      'x-razorpay-event-id': `evt_${run}`,
    },
  })
  expect(await delivered.json()).toEqual({ outcome: 'PROCESSED' })

  await expect(tenant.getByRole('heading', { name: 'All paid up' })).toBeVisible()
  await expect(tenant.getByText('Paid', { exact: true })).toBeVisible()
  await expect(
    tenant.getByRole('status').filter({ hasText: 'Razorpay confirmed your payment of ₹37,500. Receipt 0001 is ready.' }),
  ).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: "Razorpay confirmed Asha Rao's payment of ₹37,500." })).toBeVisible()
  await expect(page.getByRole('row', { name: /Asha Rao/ }).getByText('Paid', { exact: true })).toBeVisible()
  // Let the stamp settle before the screenshots.
  await tenant.waitForTimeout(700)
  await capture(tenant, testInfo, 'slip-paid')
  await capture(page, testInfo, 'book-paid')
  await expectAccessible(tenant)

  // Both keep the receipt: numbered in Lata's book, and downloadable by either of them.
  const saved = tenant.waitForEvent('download')
  await tenant.getByRole('button', { name: 'Download receipt 0001' }).click()
  const receipt = await saved
  expect(receipt.suggestedFilename()).toBe('rentbook-receipt-0001.pdf')
  if (SHOTS_DIR) await receipt.saveAs(`${SHOTS_DIR}/${testInfo.project.name}-receipt.pdf`)
  await page.getByRole('link', { name: 'Asha Rao' }).click()
  await expect(page.getByRole('row', { name: /Receipt 0001/ })).toBeVisible()
  await capture(page, testInfo, 'lease-paid')
  await expectAccessible(page)
  await tenant.getByRole('link', { name: 'Rent', exact: true }).click()
  await expect(tenant.getByRole('row', { name: /Receipt 0001/ })).toBeVisible()
  await capture(tenant, testInfo, 'rent-paid')

  // Something breaks. Asha reports it with a photo, which goes straight from her phone to storage.
  await tenant.getByRole('link', { name: 'Requests', exact: true }).click()
  await expect(tenant.getByText('Nothing reported yet.')).toBeVisible()
  await tenant.getByRole('link', { name: 'Report a problem' }).click()
  await tenant.getByLabel('What needs fixing').fill('Kitchen tap leaking')
  await tenant.getByLabel('How soon').selectOption('URGENT')
  await tenant.getByLabel('Describe it').fill('It drips all night, and the floor by the sink is wet by morning.')
  await tenant.getByLabel('Attach photos').setInputFiles({ name: 'leak.png', mimeType: 'image/png', buffer: photoOfALeak() })
  await expect(tenant.getByText('Ready', { exact: true })).toBeVisible()
  await capture(tenant, testInfo, 'request-new')
  await expectAccessible(tenant)

  await page.getByRole('link', { name: 'Sunrise PG' }).click()
  await expect(page.getByRole('heading', { name: 'Sunrise PG', level: 1 })).toBeVisible()
  await tenant.getByRole('button', { name: 'Send to Lata' }).click()
  await expect(tenant.getByRole('heading', { name: 'Kitchen tap leaking', level: 1 })).toBeVisible()
  await expect(tenant.getByRole('img', { name: 'Photo from Asha Rao' })).toBeVisible()
  await capture(tenant, testInfo, 'request-thread')

  // Lata's book hears about it at once, and the request waits under the register until it's dealt with.
  await expect(page.getByRole('status').filter({ hasText: 'Asha reported "Kitchen tap leaking".' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Needs you' })).toBeVisible()
  await capture(page, testInfo, 'book-needs-you')
  await page.getByRole('link', { name: 'Kitchen tap leaking' }).click()
  await expect(page.getByRole('img', { name: 'Photo from Asha Rao' })).toBeVisible()
  await page.getByLabel('Add to the thread').fill('The plumber comes at 10 tomorrow. Keep the tap under it closed tonight.')
  await page.getByRole('button', { name: 'Send', exact: true }).click()
  await expect(page.getByText('The plumber comes at 10 tomorrow.', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: 'Mark being fixed' }).click()
  await expect(page.getByText('Lata is having it fixed.')).toBeVisible()
  // Send re-enables once the thread has refetched; let its 150ms colour change finish before the picture.
  await expect(page.getByRole('button', { name: 'Send', exact: true })).toBeEnabled()
  await page.waitForTimeout(200)
  await capture(page, testInfo, 'request-landlord')
  await expectAccessible(page)

  // The same thread on Asha's phone, written into as Lata wrote.
  await expect(tenant.getByText('The plumber comes at 10 tomorrow.', { exact: false })).toBeVisible()
  await expect(tenant.getByText('Lata is having it fixed.')).toBeVisible()
  await expect(tenant.getByRole('status').filter({ hasText: 'Lata is having "Kitchen tap leaking" fixed.' })).toBeVisible()
  await capture(tenant, testInfo, 'request-thread-live')
  await expectAccessible(tenant)

  // Paperwork: Lata files the lease agreement on Asha's lease and Asha files her ID. One shelf, two readers.
  const aPdf = Buffer.from('%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n', 'latin1')
  await page.getByRole('link', { name: 'Back to Sunrise PG' }).click()
  await page.getByRole('link', { name: 'Asha Rao' }).click()
  await expect(page.getByRole('heading', { name: 'Documents' })).toBeVisible()
  await page.getByLabel('Choose a file').setInputFiles({ name: 'lease-agreement.pdf', mimeType: 'application/pdf', buffer: aPdf })
  await page.getByRole('button', { name: 'File it' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Filed lease-agreement.pdf.' })).toBeVisible()
  await expect(page.getByRole('list', { name: /Documents on Asha Rao/ }).getByText('lease-agreement.pdf')).toBeVisible()

  await expect(
    tenant.getByRole('status').filter({ hasText: 'Lata filed the lease agreement: lease-agreement.pdf.' }),
  ).toBeVisible()
  await tenant.getByRole('link', { name: 'Documents', exact: true }).click()
  await expect(tenant.getByText('lease-agreement.pdf')).toBeVisible()
  await tenant.getByLabel('Choose a file').setInputFiles({ name: 'aadhaar.pdf', mimeType: 'application/pdf', buffer: aPdf })
  await tenant.getByRole('button', { name: 'File it' }).click()
  await expect(tenant.getByRole('status').filter({ hasText: 'Filed aadhaar.pdf.' })).toBeVisible()
  await capture(tenant, testInfo, 'documents')
  await expectAccessible(tenant)
  const agreement = tenant.waitForEvent('download')
  await tenant.getByRole('button', { name: 'Download lease-agreement.pdf' }).click()
  expect((await agreement).suggestedFilename()).toBe('lease-agreement.pdf')

  await expect(page.getByRole('status').filter({ hasText: 'Asha filed an ID document: aadhaar.pdf.' })).toBeVisible()
  await expect(page.getByRole('list', { name: /Documents on Asha Rao/ }).getByText('aadhaar.pdf')).toBeVisible()
  await capture(page, testInfo, 'lease-documents')
  await expectAccessible(page)

  await tenantContext.close()
})
