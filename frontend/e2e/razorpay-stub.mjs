// A stand-in for Razorpay's API, for the end-to-end journey only. It answers the Route onboarding and
// order calls the backend makes; start the backend with --rentbook.razorpay.base-url pointing here.
import { randomBytes } from 'node:crypto'
import { createServer } from 'node:http'

const port = Number(process.env.RAZORPAY_STUB_PORT ?? 9911)
const id = (prefix) => `${prefix}_${randomBytes(7).toString('hex')}`

const server = createServer((request, response) => {
  let body = ''
  request.on('data', (chunk) => (body += chunk))
  request.on('end', () => {
    const json = body ? JSON.parse(body) : {}
    const path = new URL(request.url ?? '/', 'http://stub').pathname
    const send = (status, payload) => {
      response.writeHead(status, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify(payload))
    }

    if (request.method === 'GET' && path === '/health') return send(200, { ok: true })
    if (request.method === 'POST' && path === '/v2/accounts') {
      return send(200, { id: id('acc'), type: 'route', status: 'created' })
    }
    if (request.method === 'POST' && /^\/v2\/accounts\/[^/]+\/stakeholders$/.test(path)) {
      return send(200, { id: id('sth'), entity: 'stakeholder' })
    }
    if (request.method === 'POST' && /^\/v2\/accounts\/[^/]+\/products$/.test(path)) {
      return send(200, { id: id('acc_prd'), activation_status: 'requested' })
    }
    const product = path.match(/^\/v2\/accounts\/[^/]+\/products\/([^/]+)$/)
    if (request.method === 'PATCH' && product) {
      return send(200, { id: product[1], activation_status: 'activated' })
    }
    if (request.method === 'POST' && path === '/v1/orders') {
      return send(200, { id: id('order'), entity: 'order', amount: json.amount, currency: json.currency, status: 'created' })
    }
    send(404, { error: { code: 'BAD_REQUEST_ERROR', description: `The stub has no ${request.method} ${path}` } })
  })
})

server.listen(port, '127.0.0.1', () => console.log(`Razorpay stand-in on http://127.0.0.1:${port}`))
