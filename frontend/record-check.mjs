import { chromium } from '@playwright/test'
const API = 'http://localhost:8080/api/v1', APP = 'http://localhost:5173', OUT = process.argv[2]
const run = Date.now().toString(36)
const call = async (path, body, token) => {
  const res = await fetch(API + path, { method: body ? 'POST' : 'GET', headers: { 'content-type': 'application/json', ...(token ? { authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined })
  const text = await res.text(); if (!res.ok) throw new Error(`${path} ${res.status} ${text}`); return text ? JSON.parse(text) : null
}
const landlordEmail = `lata-${run}@example.in`, tenantEmail = `asha-${run}@example.in`, pw = 'correct horse battery'
await call('/auth/register/code', { email: landlordEmail })
const L = await call('/auth/register', { fullName: 'Lata Iyer', email: landlordEmail, password: pw, code: '000000' })
const prop = await call('/properties', { name: 'Sunrise PG', kind: 'PG', addressLine: '14 Koramangala 5th Block', city: 'Bengaluru', pincode: '560095' }, L.accessToken)
const unit = await call(`/properties/${prop.id}/units`, { kind: 'FLAT', label: 'Flat 2B', parentUnitId: null, defaultRentPaise: 1250000 }, L.accessToken)
const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Kolkata' })
const inv = await call(`/units/${unit.id}/invites`, { tenantName: 'Asha Rao', email: tenantEmail, phone: '+919812345678', rentPaise: 1250000, depositPaise: 2500000, dueDay: 5, startsOn: today }, L.accessToken)
const T = await call(`/invites/${inv.link.split('/').pop()}/accept`, { fullName: 'Asha Rao', password: pw })
const [lease] = await call('/leases', null, T.accessToken)
const browser = await chromium.launch()
let step = 'start', current
const signIn = async (email, size) => {
  const page = await (await browser.newContext({ viewport: size })).newPage(); current = page
  await page.goto(APP + '/login'); await page.getByLabel('Email').fill(email); await page.getByLabel('Password').fill(pw)
  await page.getByRole('button', { name: 'Sign in' }).click(); await page.waitForURL(/\/(l|t)(\/|$)/, { timeout: 15000 }); return page
}
try {
  step = 'landlord sign in'; const landlord = await signIn(landlordEmail, { width: 1440, height: 900 })
  step = 'open lease'; await landlord.goto(`${APP}/l/p/${prop.id}/leases/${lease.id}`)
  const section = landlord.locator('section', { has: landlord.getByRole('heading', { name: 'Record a payment' }) })
  await section.waitFor({ timeout: 15000 }); await section.scrollIntoViewIfNeeded()
  await section.screenshot({ path: `${OUT}/record-form.png` })
  step = 'fill form'; await landlord.getByLabel('How').selectOption('UPI'); await landlord.getByLabel('Note (optional)').fill('UPI ref 4412')
  step = 'submit'; await landlord.getByRole('button', { name: /Record .* as paid/ }).click()
  await landlord.getByText(/Receipt 0001 is issued/).waitFor({ timeout: 15000 })
  await landlord.screenshot({ path: `${OUT}/lease-after.png`, fullPage: true })
  step = 'tenant slip'; const tenant = await signIn(tenantEmail, { width: 390, height: 844 })
  await tenant.getByText('recorded by your landlord').waitFor({ timeout: 15000 })
  await tenant.screenshot({ path: `${OUT}/tenant-slip.png`, fullPage: true })
  console.log('ok')
} catch (e) {
  console.log('FAILED at', step, '-', e.message.split('\n')[0]); await current?.screenshot({ path: `${OUT}/failure.png`, fullPage: true })
  console.log('url', current?.url())
}
await browser.close()
