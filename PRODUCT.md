# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Pinned by the user, with one confirmed change:
- Backend: Spring Boot 4.1 on Java 21 with Maven. The brief asked for 3.x; the user chose 4.1 after learning that 3.5's open-source support ended on 30 Jun 2026. Spring Security (JWT, role-based), Spring Data JPA, PostgreSQL, Flyway.
- Frontend: React with role-based routing (landlord dashboard, tenant dashboard). Vite and TypeScript were delegated.
- Real-time: Spring WebSocket with STOMP.
- Payments: Razorpay Route, test mode only.
- File storage: S3-compatible (delegated choice between S3 and Cloudinary): MinIO locally, Cloudflare R2 or AWS S3 in production.
- Notifications: SendGrid email, Twilio SMS.
- Local dev on Docker Compose. Deployment on Render (backend and Postgres) and Vercel (frontend).

## Users

Two roles share one record:
- **Landlords and PG owners** in India who let whole flats, rooms, or single beds in shared PG rooms, often across several properties. They check on the go, mostly on a phone: who has paid this month, what is broken, which beds are empty. They invite tenants, answer maintenance requests, and receive rent into their own bank account.
- **Tenants**: students and working people renting a flat or a PG bed. They pay rent on a phone (UPI, cards), raise maintenance requests with photos, and need rent receipts, commonly for HRA tax claims. They join only through a landlord's invite.

## Product Purpose

One platform where a landlord and a tenant read the same rent ledger and the same maintenance thread, updated live for both, so neither keeps a private version of the truth. Rent is paid online with an automatic marketplace split: the landlord's share settles to the landlord's Razorpay linked account and the platform fee stays with the platform. A payment counts only after Razorpay's signed webhook confirms it; only then does the ledger change and a PDF receipt get issued.

Success: a tenant pays rent and holds a receipt within a minute, from a phone. A landlord sees who has not paid and what needs fixing without calling anyone.

## Positioning

Shared, not mirrored. The ledger and the maintenance thread are single records both parties read, not two copies that drift apart. Payment truth comes from the processor's verified webhook, never from anyone's claim, whether a tenant's screenshot or a browser callback. Built for how India rents: PG beds as first-class units, INR and UPI, receipts that work for HRA claims.

## Operating Context

- Phone first for both roles. Landlords check between errands, often outdoors; tenants pay on the commute or in a shared room.
- Monthly rent cycle with a due day per lease; reminders before, on, and after the due day by email and SMS.
- Invite-based onboarding: a landlord invites a tenant to a specific unit with lease terms, and the tenant signs up against that invite. There is no open tenant signup; landlords can register themselves.
- Documents: lease agreements, tenant KYC, rent receipts, maintenance photos, each visible only to the right parties.
- Money moves only in Razorpay test mode for now; the whole flow must be demonstrable with zero real money.

## Capabilities and Constraints

- Exactly two roles, LANDLORD and TENANT. A landlord's "admin view" is their own portfolio across properties and tenants; there is no platform-operator role.
- Units: a property contains flats, rooms, or beds (beds sit inside rooms). One active lease per leasable unit.
- Platform fee: a percentage of rent borne by the landlord (default 2%, configurable per landlord). The tenant pays exactly the rent.
- Payments: each landlord is onboarded as a Razorpay Route linked account; transfers are attached to the order; confirmation is webhook-verified; receipts are issued only after verification.
- Currency is INR, shown with Indian digit grouping (₹1,84,000).
- Undecided: late fees, deposit refunds, partial payments (Route forbids them on orders with transfers), regional-language UI, dark mode.

## Brand Commitments

- Working name: Rentbook, taken from the repository name and not otherwise confirmed.
- Binding visual constraints from the user. The UI must not read as AI-generated, and these are explicitly banned:
  - warm cream grounds with a terracotta or clay accent;
  - near-black grounds with a single neon-green or vermilion accent;
  - the SaaS card kit: identical rounded cards, one radius everywhere, the same soft grey shadow under every card, gradient washes as decoration;
  - tracked-out all-caps eyebrow labels;
  - meta text joined with middle dots, and em-dash labels;
  - arrow glyphs tacked onto buttons and links;
  - fade-and-slide-up on every section and hover-lift on every card. Motion, if used, is spent deliberately in one place.
- The visual language comes from the subject: homes, keys, rent, receipts, trust between two real people. Not B2B dashboard cliché.
- A deliberate named palette of 4 to 6 colors and one or two typefaces used with intention; no default system fonts.
- Landlord and tenant views are each designed for what that person needs first, not one template with swapped labels.

## Evidence on Hand

None yet: no real users, properties, testimonials, or metrics. Demo content must be labeled synthetic; never invent customers, reviews, or claims.

## Product Principles

1. One record, two readers. Never create a second copy of shared data; both roles read the same rows.
2. Money is true only when verified. No state changes on client claims; the webhook is the source of truth.
3. First things first. Each role's first screen answers its most urgent question: for the tenant, what do I owe and by when; for the landlord, who has not paid and what needs me.
4. Phone first, in real conditions: bright daylight, one hand, patchy networks.
5. Least exposure. Documents and data are visible only to the parties of the lease.

## Accessibility & Inclusion

- Required by the brief: visible keyboard focus, sufficient color contrast, respect for reduced-motion preferences. Target standard: WCAG 2.2 AA (inferred from the brief).
- Touch targets of at least 44px; fully usable at 360px width.
- Regional-language support (Hindi first) is plausible later; keep type and layout ready for Devanagari.
