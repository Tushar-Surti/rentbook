---
version: 1
slug: "frontend-src"
primary_target: "frontend/src"
related_targets: []
---

# Rentbook app (landlord and tenant surfaces)

Mode: Operate. Two roles share one record: the tenant pays and raises requests; the landlord watches collections, occupancy and requests across properties. Phone first for both, used in bright daylight and under tube lights.

This round builds: sign in, landlord registration, invite acceptance, the landlord's Book (first-run portfolio setup, register of units with occupancy and invites) and the tenant's Slip (lease summary, next rent; Pay stays disabled until payments ship). Collection status per month arrives with the ledger round.

Unresolved: Anek Latin's tabular figures (fallback face chosen with font-match if missing), dark mode (deferred; tokens ready), Devanagari support (later).

## Direction contract

THESIS: A carbon-copy rent receipt book was the original real-time sync: one stroke lands on both sheets at once. Every screen shows one shared record, written once, read by both people. It refuses the category default of sidebar, stat cards and tables.

OWN-WORLD: Sheet #F3F5F7 ground, cool and never cream. Graphite #1F2329 prints labels and rules; Carbon #2A3190 writes every entered value (amounts, dates, names), primary actions and focus rings. Stamp Violet #6B3A9E appears only on server-verified truth. Duplicate Canary #F2DA55 fills what is due now; Overdue Crimson #A3203A marks lateness and errors. Anek Latin at three widths. Square-cornered sheets with perforated edges carry the only shadow; lists are ruled rows; inputs 4px, buttons 6px.

STORY: The tenant sees what they owe and by when, trusts that violet means the bank confirmed it, and pays in one tap. The landlord sees who has not paid and what needs them without calling anyone.

FIRST VIEWPORT: Tenant at 390px: a canary duplicate slip spans the width under a perforated top edge, "Rent for October", the amount in wide Anek at display size, the due date, and a full-width Carbon Pay bar at the slip's foot. Landlord at 1440px: thumb-index tabs down the left edge, one per property; the month's register as ruled rows (unit, tenant, amount, status mark) closed by a printed total line; "Needs you" beneath. On phones the tabs become a scrolling strip above the register.

FORM: Carbon Copy, position 1 of 7 on the grounded list, chosen by the user as the pick over the rolled Key Board (position 4). Seed key 21211be9. Signature interaction: the violet PAID stamp lands on the slip and the register row flips live over STOMP when the webhook confirms payment; it is the only authored motion.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
