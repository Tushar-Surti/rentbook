# Rentbook

A shared rent book for landlords and tenants in India, covering whole flats and single beds in PG rooms. Both sides read the same records: the lease, the rent ledger and the maintenance thread are one set of rows, updated live for both people.

**Built so far:**
- Landlord registration and JWT security with two roles, LANDLORD and TENANT.
- Properties with flats, rooms and beds.
- Invite-only tenant onboarding.
- A role dashboard for each side, with live move-in notices over STOMP.
- The shared rent ledger:
  - rent generated monthly, never backdated;
  - deposits for new move-ins, and utility and other charges added by the landlord;
  - waivers;
  - live updates to both parties;
  - email and SMS reminders sent exactly once.
- Online rent through Razorpay Route, in test mode:
  - each landlord onboards as a linked account and is paid their share directly; the 2% platform fee stays with the platform;
  - a payment counts only when Razorpay's signed webhook confirms it, never on the browser's word;
  - numbered PDF rent receipts with the amount in words and the landlord's PAN, for both parties;
  - the violet PAID stamp lands live on the tenant's slip and the landlord's register.
- Maintenance requests on one shared thread:
  - the tenant reports a problem with photos, which go straight from the browser to S3-compatible storage and are checked there before they attach;
  - the request appears live under "Needs you" on the landlord's register;
  - the landlord moves it forward, and the tenant closes or reopens it;
  - both screens update as either person writes;
  - the landlord is emailed when a request comes in (and texted when it's urgent), and the tenant is emailed when it's resolved.
- A document vault per lease:
  - the landlord files the lease agreement and the tenant files their ID;
  - either can file anything else, and a landlord can keep a file to themselves;
  - only the lease's two parties see the shelf, and only whoever filed a document can remove it;
  - the other party hears about each new file live.
- Hardening:
  - a per-address throttle on new accounts;
  - nightly cleanup of abandoned uploads and expired sessions;
  - screens that load per role;
  - security headers, including a report-only CSP, on Vercel.

All five rounds of the plan are built. The design of each is in [docs/architecture.md](docs/architecture.md).

## Stack

| Part | Choice |
|---|---|
| Backend | Spring Boot 4.1 on Java 21: Spring Security 7 (stateless JWT resource server), Spring Data JPA, PostgreSQL 17, Flyway, STOMP over WebSocket |
| Frontend | React 19, TypeScript, Vite, React Router, TanStack Query, React Hook Form with Zod, `@stomp/stompjs` |
| Local infrastructure | Docker Compose: Postgres, MinIO (S3-compatible storage), Mailpit (catches email) |
| Hosting | Render (backend and Postgres), Vercel (frontend); see [docs/deployment.md](docs/deployment.md) |

## Layout

```
backend/     Spring Boot service; packages by feature under com.rentbook
frontend/    React app; landlord screens under src/landlord, tenant screens under src/tenant
docs/        architecture.md (packages, data model, API), deployment.md
compose.yaml local infrastructure
render.yaml  Render blueprint
PRODUCT.md   product truth used for design work
```

## Run it locally

You need JDK 21, Node 24 and Docker (Docker Desktop or OrbStack). On macOS with Homebrew's JDK, point Maven at it: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`.

```sh
# Backend: the dev profile starts Postgres, MinIO and Mailpit from compose.yaml on first run
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend, in another terminal
cd frontend
npm install
npm run dev
```

For online payments, copy `.env.example` to `.env` at the repo root and fill in your Razorpay test keys; the dev profile reads it, and Docker Compose passes it to the backend container. `.env` is gitignored. The browser gets the key id from the server with each order, so the frontend needs no Razorpay variable, and the key secret never leaves the backend.

- App: http://localhost:5173. Vite proxies `/api` and `/ws` to the backend on port 8080.
- API docs (dev only): http://localhost:8080/swagger-ui.html
- Invite emails: http://localhost:8025 (Mailpit)

To run everything in containers instead: `docker compose --profile app up --build`.

Without Docker, point the backend at any Postgres 17:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments="\
  --spring.docker.compose.enabled=false \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/rentbook \
  --spring.datasource.username=rentbook --spring.datasource.password=rentbook"
```

Emails then fail to send (there is no Mailpit), which the backend logs. The invite page still shows the link to copy.

Photo uploads also need S3-compatible storage. Without Docker, run the MinIO binary:

```sh
MINIO_ROOT_USER=rentbook MINIO_ROOT_PASSWORD=rentbook-secret MINIO_API_CORS_ALLOW_ORIGIN=http://localhost:5173 \
  minio server ./minio-data --address 127.0.0.1:9000
```

Then add `--rentbook.storage.endpoint=http://127.0.0.1:9000` to the backend arguments. The dev profile creates the bucket.

## Tests

```sh
cd backend && ./mvnw verify     # unit tests, then integration tests against a Postgres container
cd frontend && npm test         # unit tests
cd frontend && npm run build    # type-check and production build
cd frontend && npm run e2e      # browser journey on phone and desktop sizes, with axe checks
```

`npm run e2e` drives the running app (backend on 8080, `npm run dev` on 5173). The journey:
1. A landlord registers, adds a PG room with beds, sets up payouts and invites a tenant.
2. The tenant accepts in a second browser, and the landlord's page updates live.
3. The two share a ledger. The tenant pays through Checkout and waits until a signed webhook confirms the payment.
4. The stamp lands on both screens, and the receipt downloads.

It never touches Razorpay: Playwright starts a stand-in API, and the backend must point at it. The exact command is in [frontend/README.md](frontend/README.md). Set `SHOTS_DIR` to keep a screenshot of every screen. The first run needs `npx playwright install chromium`.

The integration tests need Docker for Testcontainers. To run them against an existing Postgres instead, add `-Drentbook.test.external-db=true -Dspring.datasource.url=jdbc:postgresql://localhost:5432/rentbook_test -Dspring.datasource.username=… -Dspring.datasource.password=…`.

What they cover:
- **Security matrix:** each role against every protected endpoint; forged, expired and wrong-issuer tokens; foreign origins on the cookie endpoints.
- **Auth:** refresh rotation, reuse detection, logout, login throttling.
- **Invites and leases:** the invite flow, including revoked, expired and reused links; tenants and other landlords get 404 on leases that aren't theirs.
- **Live updates:** STOMP CONNECT without a token is refused, SUBSCRIBE to someone else's lease is refused, and a live move-in event reaches the landlord.
- **Ledger:** rent generated once a month and never backdated, charges added and waived, and each reminder sent once.
- **Payments,** against a WireMock Razorpay:
  - onboarding, including a retry that resumes after a refusal;
  - the split order, with the landlord's share at 98%;
  - a browser callback that only waits, and refusal of a second payment while one waits;
  - rejection of forged webhook signatures, duplicate events and wrong amounts;
  - receipts numbered per landlord and readable only by the lease's two parties.
- **Maintenance,** against a WireMock S3:
  - one thread both parties write on;
  - status moves limited by role;
  - 404 for strangers, over REST and over STOMP;
  - uploads confirmed in the bucket before they attach;
  - refusal of wrong file types, oversized files and reused photos.
- **Vault:**
  - who sees what on a lease's shelf (shared, landlord-only, strangers get 404);
  - who files which kinds of document;
  - removal by the filer only, including the object in storage;
  - thread photos kept off the shelf;
  - abandoned uploads cleared after a day.
- **Safeguards:** the per-address signup throttle, and the purge of expired sessions.

## Design

The UI direction is "Carbon Copy": the carbon-copy rent receipt book, where one stroke lands on both sheets at once. Product context is in [PRODUCT.md](PRODUCT.md), the direction contract in `.impeccable/surfaces/frontend-src.md`, and the visual system is documented in `DESIGN.md`.
