# Rentbook frontend

The React app for both roles: landlords work in `/l`, tenants in `/t`. It uses Vite, React 19, TypeScript, React Router, TanStack Query, React Hook Form with Zod, and CSS Modules over the Carbon Copy tokens in `src/design/tokens.css`.

## Run it

```sh
npm install
npm run dev        # http://localhost:5173, proxying /api and /ws to the backend on :8080
```

Razorpay needs no frontend variable: Checkout's script loads when a tenant first presses Pay, and the key id comes from the server with each order (`POST /api/v1/payments/checkout`). The key secret stays in the backend's `.env` or environment.

Set `VITE_PROXY_TARGET` to proxy somewhere else. In production Vercel serves the app and rewrites `/api` to the backend (see `vercel.json`). Vercel can't proxy WebSockets, so `VITE_WS_URL` points the live channel straight at the backend.

## Checks

| Command | What it does |
|---|---|
| `npm run build` | Type-checks, then builds to `dist/` |
| `npm test` | Unit tests (Vitest) |
| `npm run e2e` | The whole journey in Chromium at phone and desktop sizes, with axe checks on each screen |

The journey pays rent without touching Razorpay:
- Playwright starts a stand-in Razorpay API on :9911 (`e2e/razorpay-stub.mjs`).
- It serves a fake Checkout script that signs the payment the way Razorpay does.
- It posts a signed `order.paid` webhook.

For this to work, the backend on :8080 has to point at the stand-in and use the matching test secrets:

```sh
java -jar ../backend/target/rentbook-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev \
  --rentbook.razorpay.base-url=http://127.0.0.1:9911 \
  --rentbook.razorpay.key-id=rzp_test_e2e \
  --rentbook.razorpay.key-secret=e2e-key-secret \
  --rentbook.razorpay.webhook-secret=e2e-webhook-secret
npm run dev
SHOTS_DIR=../screenshots npm run e2e
```

`SHOTS_DIR` is optional; set it to keep a full-page screenshot of every screen. The dev profile starts Postgres, MinIO and Mailpit through Docker Compose.

## Layout

```
src/
  app/        router, role guards, error pages
  api/        fetch client (access token in memory, one refresh on a 401) and the API's types
  auth/       session provider, sign-in and registration
  realtime/   the STOMP live channel
  design/     tokens, global styles, and the components: Sheet, Button, Field, Mark, Stamp, Icon
  landlord/   the book: property register, invites, leases, payouts
  tenant/     the slip, the tenant's rent book, paying
  ledger/     the ledger table both roles read
  receipts/   receipt list and PDF download
  lib/        en-IN formatting, Razorpay Checkout, validation
```
