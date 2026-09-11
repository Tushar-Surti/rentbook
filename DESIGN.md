---
name: Rentbook
description: A shared rent record for landlords and tenants, drawn as a carbon-copy receipt book.
colors:
  sheet: "#f3f5f7"
  sheet-original: "#ffffff"
  rail: "#e4e8ee"
  rule: "#c9ced6"
  rule-strong: "#8a93a0"
  graphite: "#1f2329"
  graphite-soft: "#4a515c"
  carbon: "#2a3190"
  carbon-press: "#1e2470"
  carbon-wash: "#e2e5f5"
  on-carbon: "#ffffff"
  stamp: "#6b3a9e"
  canary: "#f2da55"
  crimson: "#a3203a"
  crimson-wash: "#f8e3e7"
typography:
  display:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "3rem"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.02em"
    fontFeature: "'tnum'"
    fontVariation: "'wdth' 118"
  headline-lg:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "2.074rem"
    fontWeight: 650
    lineHeight: 1.15
    letterSpacing: "-0.01em"
  headline-md:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "1.728rem"
    fontWeight: 650
    lineHeight: 1.15
    letterSpacing: "-0.01em"
  title-lg:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "1.44rem"
    fontWeight: 650
    lineHeight: 1.15
    letterSpacing: "-0.01em"
  title-md:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "1.2rem"
    fontWeight: 650
    lineHeight: 1.15
    letterSpacing: "-0.01em"
  body:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  body-strong:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 600
    lineHeight: 1.5
    letterSpacing: "normal"
  body-sm:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  small-strong:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 550
    lineHeight: 1.5
    letterSpacing: "normal"
  entry-lg:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "1.2rem"
    fontWeight: 550
    lineHeight: 1.5
    letterSpacing: "normal"
  label:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 600
    lineHeight: 1.5
    letterSpacing: "normal"
  button:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: "normal"
  button-quiet:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 550
    lineHeight: 1.1
    letterSpacing: "normal"
  register:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
    fontFeature: "'tnum'"
    fontVariation: "'wdth' 82"
  tab:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 600
    lineHeight: 1.5
    letterSpacing: "normal"
    fontVariation: "'wdth' 82"
  wordmark:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "1.2rem"
    fontWeight: 700
    lineHeight: 1.5
    letterSpacing: "-0.01em"
    fontVariation: "'wdth' 82"
  stamp-mark:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 700
    lineHeight: 1.4
    letterSpacing: "normal"
  stamp-slip:
    fontFamily: "'Anek Rupee', 'Anek Latin Variable', system-ui, sans-serif"
    fontSize: "1.728rem"
    fontWeight: 700
    lineHeight: 1.1
    letterSpacing: "normal"
    fontVariation: "'wdth' 118"
rounded:
  sheet: "0px"
  stamp: "3px"
  input: "4px"
  tab: "4px"
  button: "6px"
spacing:
  space-1: "0.25rem"
  space-2: "0.5rem"
  space-3: "0.75rem"
  space-4: "1rem"
  space-5: "1.5rem"
  space-6: "2rem"
  space-7: "3rem"
  space-8: "4rem"
components:
  button-primary:
    backgroundColor: "{colors.carbon}"
    textColor: "{colors.on-carbon}"
    typography: "{typography.button}"
    rounded: "{rounded.button}"
    padding: "0 1.5rem"
    height: "2.75rem"
  button-primary-hover:
    backgroundColor: "{colors.carbon-press}"
    textColor: "{colors.on-carbon}"
  button-primary-disabled:
    backgroundColor: "{colors.rule}"
    textColor: "{colors.graphite-soft}"
  button-secondary:
    backgroundColor: "transparent"
    textColor: "{colors.carbon}"
    typography: "{typography.button}"
    rounded: "{rounded.button}"
    padding: "0 1.5rem"
    height: "2.75rem"
  button-secondary-hover:
    backgroundColor: "{colors.carbon-wash}"
    textColor: "{colors.carbon}"
  button-quiet:
    backgroundColor: "transparent"
    textColor: "{colors.carbon}"
    typography: "{typography.button-quiet}"
    padding: "0.25rem 0"
  row-action:
    backgroundColor: "transparent"
    textColor: "{colors.carbon}"
    typography: "{typography.small-strong}"
    padding: "0.25rem 0"
  back-link:
    textColor: "{colors.carbon}"
    typography: "{typography.body-strong}"
  text-field:
    backgroundColor: "{colors.sheet-original}"
    textColor: "{colors.carbon}"
    typography: "{typography.body}"
    rounded: "{rounded.input}"
    padding: "0 0.75rem"
    height: "2.75rem"
  text-area:
    backgroundColor: "{colors.sheet-original}"
    textColor: "{colors.carbon}"
    typography: "{typography.body}"
    rounded: "{rounded.input}"
    padding: "0.5rem 0.75rem"
    height: "6rem"
  field-label:
    textColor: "{colors.graphite}"
    typography: "{typography.label}"
  field-prefix:
    textColor: "{colors.graphite-soft}"
    typography: "{typography.body}"
    padding: "0 0.75rem"
  field-hint:
    textColor: "{colors.graphite-soft}"
    typography: "{typography.body-sm}"
  field-error:
    textColor: "{colors.crimson}"
    typography: "{typography.small-strong}"
  form-error:
    backgroundColor: "{colors.crimson-wash}"
    textColor: "{colors.crimson}"
    padding: "0.75rem 1rem"
  link-to-copy:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.carbon}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.input}"
    padding: "0 0.75rem"
    height: "2.75rem"
  sheet-original:
    backgroundColor: "{colors.sheet-original}"
    textColor: "{colors.graphite}"
    rounded: "{rounded.sheet}"
    padding: "2rem 1.5rem 1.5rem"
  sheet-duplicate:
    backgroundColor: "{colors.canary}"
    textColor: "{colors.graphite}"
    rounded: "{rounded.sheet}"
    padding: "2rem 1.5rem 1.5rem"
  print:
    backgroundColor: "{colors.sheet-original}"
    rounded: "{rounded.sheet}"
    padding: "4px"
    size: "7.5rem"
  landlord-page:
    backgroundColor: "{colors.sheet-original}"
    textColor: "{colors.graphite}"
    padding: "1.5rem 1rem 3rem"
  index-tab:
    backgroundColor: "{colors.rail}"
    textColor: "{colors.graphite}"
    typography: "{typography.tab}"
    rounded: "{rounded.tab}"
    padding: "0.5rem 1rem"
    height: "2.75rem"
  index-tab-active:
    backgroundColor: "{colors.sheet-original}"
    textColor: "{colors.carbon}"
  tenant-tab:
    backgroundColor: "{colors.rail}"
    textColor: "{colors.graphite}"
    typography: "{typography.tab}"
    rounded: "{rounded.tab}"
    padding: "0.5rem 1.5rem"
    height: "2.75rem"
  tenant-tab-active:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.carbon}"
  top-link:
    textColor: "{colors.carbon}"
    typography: "{typography.label}"
    height: "2.75rem"
  live-line:
    textColor: "{colors.carbon}"
    typography: "{typography.body-strong}"
    padding: "0 1rem"
  wordmark:
    textColor: "{colors.graphite}"
    typography: "{typography.wordmark}"
  ledger-head:
    textColor: "{colors.graphite-soft}"
    typography: "{typography.label}"
    padding: "0.5rem 0.75rem"
  ledger-cell:
    textColor: "{colors.graphite}"
    typography: "{typography.register}"
    padding: "0.75rem"
  ledger-entry:
    textColor: "{colors.carbon}"
    typography: "{typography.register}"
  ledger-waived:
    textColor: "{colors.graphite-soft}"
    typography: "{typography.register}"
  thread-byline:
    textColor: "{colors.graphite}"
    typography: "{typography.label}"
  thread-body:
    textColor: "{colors.carbon}"
    typography: "{typography.body}"
  thread-status-line:
    textColor: "{colors.graphite}"
    typography: "{typography.label}"
    padding: "0.75rem 0"
  mark-upcoming:
    textColor: "{colors.graphite-soft}"
    typography: "{typography.label}"
  mark-vacant:
    textColor: "{colors.graphite-soft}"
    typography: "{typography.label}"
  mark-invited:
    textColor: "{colors.carbon}"
    typography: "{typography.label}"
  mark-waived:
    textColor: "{colors.graphite-soft}"
    typography: "{typography.label}"
  mark-working:
    textColor: "{colors.graphite}"
    typography: "{typography.label}"
  mark-urgent:
    textColor: "{colors.graphite}"
    typography: "{typography.label}"
  mark-done:
    textColor: "{colors.graphite-soft}"
    typography: "{typography.label}"
  mark-due:
    backgroundColor: "{colors.canary}"
    textColor: "{colors.graphite}"
    typography: "{typography.label}"
    padding: "0 0.5rem"
  mark-overdue:
    backgroundColor: "{colors.crimson-wash}"
    textColor: "{colors.crimson}"
    typography: "{typography.label}"
    padding: "0 0.5rem"
  stamp-mark:
    textColor: "{colors.stamp}"
    typography: "{typography.stamp-mark}"
    rounded: "{rounded.stamp}"
    padding: "0 0.5rem"
  stamp-slip:
    textColor: "{colors.stamp}"
    typography: "{typography.stamp-slip}"
    rounded: "{rounded.stamp}"
    padding: "0.05em 0.4em 0.1em"
  pay-waiting:
    textColor: "{colors.graphite}"
    typography: "{typography.body-strong}"
---

# Design System: Rentbook

## Overview

**Creative North Star: "Carbon Copy"**

A carbon-copy receipt book was the first real-time sync: one stroke of the pen lands on the original and the duplicate at once. Rentbook draws every screen as that book. The ground is cool paper, printed labels are graphite, and everything written into the record (amounts, dates, names, whatever a person types) is in carbon-blue ink, the same ink on the landlord's page and on the tenant's slip. The landlord keeps the book, one thumb-index tab per property and a ruled ledger for every lease; the tenant holds the canary duplicate and reads the very same ledger rows. The same book holds each maintenance request as a ruled thread both people write in, with photos pinned in as prints, and a shelf of the lease's documents.

The system is dense where it records and roomy where it asks. The register, the ledger, the receipts, the requests and the shelf are ruled rows with tabular figures, a heavy rule under the headings and a double rule over any total; forms and the tenant's slip get space, and the one amount a tenant came for is set wide and heavy. Depth is paper: square sheets with a perforated top edge, and square photo prints, carry the only shadow. Colour is spent by meaning: Canary is the duplicate and what must be paid today, Crimson is late or wrong, and Stamp Violet is pressed on only when Razorpay confirms a payment; a charge not yet due is said quietly, and urgency is carried by order, not by red. Nothing billed ever leaves the book; a waived line stays, struck through. When one reader changes the book, or the bank confirms a payment, a live line under the other's top bar says who did it and what.

It is not the SaaS dashboard kit of sidebar, stat cards and identical rounded cards under one grey shadow, not a warm cream ground with a clay accent, and not a near-black ground with a single neon accent. The interface is light only for now; dark mode is deferred, and the role-named tokens are the seam it will use.

**Key Characteristics:**
- A cool Sheet-grey ground, white original sheets and canary duplicates; never cream.
- Two inks: Graphite prints the form, Carbon writes the record, including every word a person writes into a thread.
- One family, Anek Latin, at three widths, with tabular figures and a rupee sign that matches, in the app and on the receipt PDF.
- Square sheets, perforated along the top edge, and square photo prints carry the system's only shadow.
- Ruled rows instead of cards, closed by ledger rules; on phones each row folds into two lines.
- One ledger shared by both roles, with waived lines kept and struck through, and a receipt for every confirmed payment.
- Canary kept for today; an upcoming charge is a quiet Soft Graphite mark, and an urgent request is a plain Graphite one, listed first.
- A violet Paid stamp only on payments the server has verified.
- A live line under each top bar saying who changed the shared book.
- Properties as thumb-index tabs cut into the page's left edge on wide screens; the tenant's Home, Rent, Requests and Documents tabs sit over the content column.
- Motion spent once: the Paid stamp landing when a confirmation arrives while someone is looking.

## Colors

A cool paper palette with two inks and three marks, each mark kept for one meaning.

### Primary
- **Carbon** (#2a3190): the ink of the record. Every entered value (amounts, dates, tenant and landlord names, places, email addresses, bank details, receipt numbers, request titles, filenames and the words of a thread message, written as values, and typed input text), primary buttons, links, the open tab's label, the top bar's links, the live status line on both roles' screens, a form's "Added ..." or "Filed ..." confirmation, the checkbox accent, the text caret and focus rings.
- **Carbon Press** (#1e2470): the primary button under the pointer, and nothing else.
- **Carbon Wash** (#e2e5f5): the secondary button's hover fill and text selection.

### Secondary
- **Duplicate Canary** (#f2da55): the tenant's copy and what must be paid today. It fills the invite's duplicate and the terms on the accept page, the Due today mark in the register and the ledger, and the tenant's slip while any charge on it is due today or overdue, including while a payment waits for the bank; it also dots the wordmark's slip. A slip whose charges are all still upcoming, and a paid-up slip, are white originals.

### Tertiary
- **Stamp Violet** (#6b3a9e): stamp-pad ink, reserved for a payment the server has verified through Razorpay's signed webhook. It appears only as the Paid stamp: at mark size in the ledger's status column and the register's month column, at slip size across the paid slip's amount, and drawn on the receipt PDF. A payment still waiting for the bank never shows it, and neither does a request marked Resolved.

### Error
- **Overdue Crimson** (#a3203a): lateness and errors: the Overdue mark (in the register, the ledger and on the slip), the "of it overdue" note under a ledger's total, a field's error line and border, a form-level failure, a failed row action, download, status move or photo upload, and a declined or failed payment on the slip. The tenant's lease-on-notice warning also uses it. Urgency never does.
- **Crimson Wash** (#f8e3e7): the band behind a form-level error and the fill of the Overdue mark.

### Neutral
- **Sheet** (#f3f5f7): the ground everything lies on, cool and never cream; the default colour of perforation holes, the tenant's open tab, the field behind a link to copy, and the flat panel that holds a freshly issued invite link.
- **Original White** (#ffffff): the original sheet, the landlord's open page and open tab, the inside of every field, and the border of a photo print.
- **Rail** (#e4e8ee): unopened tabs and the placeholder bars of a loading sheet.
- **Rule** (#c9ced6): hairline row rules (in the app and on the receipt PDF), a field prefix's divider, tab outlines, the line under the tenant's tabs, and the disabled primary fill.
- **Strong Rule** (#8a93a0): field borders, the dashed box around a link to copy, the scrollbar thumb.
- **Graphite** (#1f2329): printed labels, headings, unit labels, ledger line descriptions, a request's working and Urgent marks, a thread's bylines and status lines, a document's type label, the slip's waiting line and the heavy rules.
- **Soft Graphite** (#4a515c): secondary print: addresses, ledes, hints, column heads, the lease's term labels, a receipt's items, a request's where-line and category, times, filing notes, a photo's upload state, the closed-request note, upcoming, vacant, waived and done marks, waived ledger lines, placeholders, empty date masks, disabled labels, and the labels and proof line on the receipt PDF.
- **On-Carbon White** (#ffffff): the label on a Carbon button.

On a canary sheet, rows are ruled in Graphite at 22% and the slip's tear line in Graphite at 35%, so both read on yellow. An unopened tab lightens on hover to a 60/40 mix of Rail and Original White. Every text pairing in use clears WCAG AA: Carbon reads 10.8:1 on white and 7.7:1 on Canary, Soft Graphite 7.3:1 on Sheet and 5.7:1 on Canary, Stamp Violet 7.7:1 on white, Crimson 6.1:1 on Crimson Wash, and Strong Rule field borders 3.1:1 on white.

### Named Rules
**The Two Inks Rule.** Graphite prints the form; Carbon writes the record. A label, heading or column head is Graphite; a value someone entered (an amount, a date, a name, a filename, the words of a message, typed text) is Carbon, and a date the book writes stays Carbon at any size. When an entered name becomes structure (a property's title, a unit's label, a tab, a ledger line's description of what it is for, a request's title as a page heading), it is printed and takes Graphite. A secondary note on a row (an asking rent, a link's expiry, a receipt's items, who filed a document and when) is printed small and soft, so the row's Carbon entry stays the one to read.

**The Earned Stamp Rule.** Stamp Violet appears only where the server has verified a payment: the Paid stamp in the ledger, the register, the paid slip and the receipt PDF. Never use it for emphasis, links, decoration, a client-side claim, a payment still waiting for the bank, or a status a person sets; Resolved is the landlord's word, not a verified fact.

**The Canary Means Now Rule.** Outside the wordmark, Duplicate Canary fills only the duplicate copy and what must be paid now: the Due today mark, and the tenant's slip while anything on it is due today or late. A charge not yet due is said quietly in Soft Graphite, a slip with nothing due now is white, and canary is never a general highlight or hover colour.

**The Late or Wrong Rule.** Overdue Crimson means late or wrong and nothing else. An urgent request is printed plain in Graphite and carried by order, urgent first; it is never red.

## Typography

**Display Font:** Anek Latin Variable at 118% width (with system-ui, sans-serif)
**Body Font:** Anek Latin Variable at 100% width (with system-ui, sans-serif)
**Label/Mono Font:** Anek Latin Variable at 82% width for registers, ledgers, tabs and the wordmark; there is no mono face

**Character:** One family doing three jobs by width alone: condensed like a ledger column, normal for the interface, wide for the one number that matters and for the stamp. The rupee sign comes from Anek Devanagari, Anek Latin's sister face, through a 6 kB subset (SIL OFL, kept in `src/design/fonts/`) declared as 'Anek Rupee' for U+20B9 only and set first in the stack, so ₹ matches the figures beside it. The receipt PDF embeds static instances of the same faces, so paper and screen share one hand.

### Hierarchy
- **Display** (700, 3rem, 3.75rem from 48rem, line-height 1, -0.02em, 118% width, tabular): the tenant's amount on the slip (what is owed, or what a confirmed payment settled), in Carbon. One per screen.
- **Headline Large** (650, 2.074rem, 1.15, -0.01em): landlord page titles (a property's name in the Book, the tenant's name on a lease page, New property, Invite a tenant, "Get paid online" and "Online rent is on") and a request's title at the head of its thread, for both roles.
- **Headline Medium** (650, 1.728rem, 1.15): the heading of a tenant page or of a single-sheet or message page: "Your rent book", "Requests", "Report a problem", "Documents", "All paid up" on a slip with no receipt, sign in, register, accept an invite, no active lease, errors.
- **Title Large** (650, 1.44rem, 1.15): a section inside a page: "Rent book", "Receipts", "Add a charge" and "Documents" on the lease page, "Needs you" and "Add to Sunrise PG" in the Book, "File a document", "Your home", the accept form.
- **Title Medium** (650, 1.2rem, 1.15): a sheet part's own heading ("The terms", "What Asha Rao will see", "Receipts" on the tenant's Rent page, "On file" on the tenant's Documents page) and form legends; the slip's heading ("Due now", "Outstanding", "Coming up", the charge's description, or "All paid up" over a stamped amount) sets it at 600.
- **Body** (400, 1rem, 1.5, 100% width): running text, list values and a thread message's words. Paragraphs and messages stop at 68ch and wrap with pretty breaks; a message keeps its writer's line breaks. Headings balance their lines.
- **Body Strong** (600, 1rem): the live status lines, back links ("All requests", "Back to Sunrise PG"), a request's title in a list, the "1 request needs you" link, Call and Email links, "Added ..." and "Filed ..." confirmations, the checkbox label, and the slip's waiting line.
- **Entry Large** (550, 1.2rem): values on a duplicate's terms list and on the lease's terms strip.
- **Register** (400, 1rem, 82% width, tabular; 1.2rem from 64rem): register, ledger and receipts body cells. Unit labels and totals at 650; on phones a ledger line's description at 600.
- **Label** (600, 0.875rem): field labels, list terms, column heads (in Soft Graphite), status marks, the top bar's links, a thread's bylines and status lines, a document's type label, and an inline confirm's question. Sentence case, never tracked out.
- **Small** (400, 0.875rem; tallies and "of ₹..." at 500): hints, notes, asking prices, link expiry lines, a receipt's items, a bill line's own due date (in Carbon), a request's where-line and category, times (tabular), who filed a document, and a photo's upload state.
- **Small Strong** (550, 0.875rem): a field's error line and row actions; a declined payment's line and a failed photo set it at 600.
- **Button** (600, 1rem, 1.1): every boxed button label; quiet text buttons set it at 550.
- **Stamp** (700): "Paid" at 0.875rem (line-height 1.4) as a mark; at 1.728rem, 2.074rem from 48rem, 118% width and line-height 1.1 on the slip.

Headings climb a 1.2 ratio in fixed rem (1.2, 1.44, 1.728, 2.074rem). Nothing is fluid: the display amount and the slip stamp step up at 48rem, and register, ledger and receipts body cells step up to 1.2rem from 64rem while their secondary text holds at 0.875rem, so the condensed rows stay dense rather than small.

### Named Rules
**The Three Widths Rule.** One family, three widths, one job each: 82% for registers, ledgers, tabs and the wordmark; 100% for the interface; 118% only for the tenant's amount and the stamp. Change the width to change the voice; never reach outside the Anek family.

**The Tabular Figures Rule.** Every figure in data (amounts, totals, ledger dates, due days, receipt numbers, times in a thread, typed numbers) is set in tabular figures so columns align like a ledger.

**The Real Rupee Rule.** ₹ always comes from the 'Anek Rupee' subset, first in the font stack (and embedded in the receipt PDF), and amounts use Indian digit grouping (₹1,84,000). Never let ₹ fall through to a system face.

## Layout

Spacing runs on a 4px-based scale (0.25, 0.5, 0.75, 1, 1.5, 2, 3, 4rem). Fields stack 1rem apart, the blocks of the Book and lease pages sit 3rem apart, and a sheet's padding is 2rem at the top and 1.5rem at the sides and bottom on phones, 3rem and 2rem from 48rem; the extra top room clears the perforation. Every control is at least 2.75rem (44px) tall. Row actions keep their small text and extend an invisible hit area to 44px (12px above and below, 6px to each side), so every ruled row keeps one pitch.

Measures are fixed, not fluid. Paragraphs stop at 68ch. The landlord's Book and lease pages hold every block to one 52rem measure, so the register or ledger, the receipts, the requests, the shelf, the terms strip, the empty states and the forms share a right edge, and the ledger, receipts, request list, thread and shelf keep that 52rem measure wherever they appear. The invite spread widens to 64rem because it holds two sheets; New property and Payouts are 36rem. The tenant's content column is 64rem, centred, and every tenant page starts at its left edge, including the 36rem report form. A single-sheet page (sign in, register) is 29rem, message pages 36 to 40rem, the accept page 64rem, and a link to copy at most 32rem. The landlord's page and the tenant's main column clip horizontal overflow, so a landing stamp never widens the page.

The landlord's screens are a book. A top bar carries the wordmark, Add property (wide screens), Payouts, the landlord's name and Sign out; beneath it a live status line writes real-time events in Carbon. The open page is Original White on the Sheet ground, padded 1.5rem 1rem 3rem on phones and 2rem 3rem 4rem from 64rem. On phones the properties are a horizontally scrolling strip of index tabs above the page, ending in an Add property tab; from 64rem they stack down the left edge, sticky, sized to their names and cut into the page's 1px left rule. A property's page runs header, register, "Needs you" (only while requests are open) and "Add to ..."; a lease page runs terms, "Rent book", "Receipts", "Add a charge" and "Documents"; a request's page opens with a back link to its property above the thread.

The tenant's screens lie on the Sheet ground: a top bar, a live status line in Carbon, then four tabs, Home, Rent, Requests and Documents, set on a 1px Rule line; from 48rem the live line and the tabs start where the 64rem content column starts, and below 30rem the tabs' side padding drops to 0.75rem so all four fit a 360px phone. Home leads with the slip: on phones it sits first at full width with "Your home" below; from 56rem the slip holds a sticky 26rem column beside it. Rent holds the ledger and then the receipts, each on its own perforated original sheet at the 52rem measure, set against the column's left edge. Requests runs heading, lede, Report a problem and the list; a request's page opens with "All requests" above the thread. Documents runs heading, lede, the "On file" sheet and "File a document".

Under 40rem every ruled table folds each row into a two-line grid: the register shows unit and rent above, tenant and due date (or the invite action) below; the ledger shows what the line is for and its amount above, the due date and status below, with any action on a third line. The column heads hide and the heavy rule moves to the top of the list. Request and shelf lines wrap their marks and actions under their text when space runs out. The invite spread stacks original over duplicate on phones and sits side by side from 60rem, at 1.2 to 0.8; the accept page goes to two columns from 56rem.

Breakpoints: 30rem (paired fields sit side by side; the user's name shows in the top bar; the tenant's tabs take their full padding), 40rem (tables keep their columns; the filing form's type and file chooser sit side by side), 48rem (roomier sheets and pages; the display amount and the slip stamp step up; a thread's prints grow to 9rem; the tenant's live line and tabs align to the column), 56rem (the slip beside "Your home"; the accept page in two columns), 60rem (the invite spread side by side), 64rem (index tabs move to the left edge; Add property moves to the top bar; table rows step up to 1.2rem).

## Elevation & Depth

Depth is paper and nothing else. Surfaces are flat and separated by rules; the only lift in the system is the soft shadow under paper, which says that this is a piece of paper lying on the ground. There is no tonal elevation stack: the ground is Sheet grey, the landlord's open page is Original White, unopened tabs are Rail, and none of them casts a shadow. Two sheets laid edge to edge (the invite spread) share one shadow, as one torn spread would. The stamp adds no depth: its ink multiplies into the paper.

### Shadow Vocabulary
- **Sheet** (`box-shadow: 0 1px 1px rgb(31 35 41 / 0.06), 0 8px 20px -10px rgb(31 35 41 / 0.28)`): under every piece of paper: the sign-in and register sheets, the rent slip, "Your home", the tenant's rent book, receipts and "On file" sheets, the accept page's sheets, the invite spread, the blank sheet shown while loading, and every photo print, in a thread or waiting to be sent.

### Named Rules
**The Only Shadow Rule.** The sheet shadow is the only shadow and it belongs to paper: sheets and photo prints. Buttons, fields, tabs, marks, stamps and rows never cast one, and nothing lifts on hover.

## Shapes

Paper is square and controls are barely rounded. Sheets have no radius at all; fields take 4px, buttons 6px, tabs 4px on their free corners only (the top corners in a strip, the left corners down the page edge), and the Paid stamp 3px inside its double ring (3px as a mark, 4px on the slip). A sheet's top edge is perforated: half-punched holes 3.5px in radius every 14px, painted in the colour of whatever the sheet lies on. That colour is `--hole`, which defaults to Sheet grey and is set to Original White on the landlord's page. Where the invite's duplicate tears from the original, its tear edge is punched in Original White: along its top when stacked, down its left edge when side by side.

A photo is a print: a square of photo paper, 7.5rem (9rem in a thread from 48rem), the picture cropped to fill it (object-fit: cover) inside a 4px Original White border, square-cornered like every sheet.

Lines do the structural work that cards do elsewhere: a 1px Rule hairline under each row, a 2px Graphite rule under column heads or over a list, a thread, a shelf or a printed account (and over a table's rows on phones), a 3px double Graphite rule over a total, the lease's terms printed across one band between two hairlines, a dashed tear line on the slip above the Pay bar, and a dashed Strong Rule box around a link meant to be copied out. The only things set off square are stamps: -3 degrees as a mark, -8 degrees on the slip and the PDF. Icons are drawn on a 20px grid in one 1.5px round-capped stroke (plus, copy, check, phone, mail, door), and the date picker's glyph is redrawn to match.

### Named Rules
**The Square Paper Rule.** Sheets and prints are square; no surface or control takes a radius above 6px.

**The Hole Follows the Ground Rule.** Perforation holes take the colour of whatever lies behind that edge: the ground under a sheet's top, set as `--hole` on the parent surface, or the original beside a torn duplicate.

## Components

### Buttons
Pressed Carbon ink, not glossy chrome.
- **Shape:** gently squared corners (6px), at least 44px tall, 1.5rem side padding.
- **Primary:** Carbon fill with a white label at 600. One per view, for the step forward: the submit on sign in, registration and New property, Send invite, Accept invite, Add charge, Set up payouts, Send on WhatsApp once a tenant's mobile is known, Pay, Add while a property has no units, Report a problem, Send to Lata, Send in a thread, and File it on the tenant's Documents page.
- **Hover / Focus:** hover deepens to Carbon Press over 150ms; focus draws the 2px Carbon ring 2px outside; a press sinks the button 1px.
- **Secondary:** transparent with a 1px Carbon border and a Carbon label; hover fills Carbon Wash. Used for Copy link, Download receipt on the paid slip, Add once a property has units, the accept page's sign-out, a request's status moves, and File it on the lease page, where Add charge is already the primary.
- **Label-buttons:** a file chooser is a secondary button drawn on the label of a visually hidden file input ("Attach photos" with the plus icon, "Choose a file"); it shows the focus ring when the input inside it has focus.
- **Quiet:** an underlined Carbon label at 550 (1px underline at 0.2em offset, 2px on hover) with no box: Cancel, Done, Sign out.
- **Row actions:** quiet buttons at 0.875rem (Invite a tenant, New link, Withdraw, Waive, Download, Remove) with an invisible 44px hit area. A consequential one asks inline, in the book, never in a modal. A row action's accessible name names its row where the label alone would not ("Download receipt 0001", "Remove aadhaar.pdf").
- **Disabled and busy:** a disabled primary turns Rule grey with a Soft Graphite label, as the Pay bar does while the landlord has no active payouts. A busy button is disabled, shows the progress cursor and, where it has words to spare, relabels itself ("Sending", "Adding", "Setting up", "Filing").
- **Icons:** 18 to 20px, left of the label, from the house set. Never an arrow.

### Marks
Status is written beside an entry, not wrapped in a pill.
- **Plain marks:** coloured label text (0.875rem, 600). Soft Graphite for what is quiet or finished: Upcoming, Vacant, Not in use, Waived, and a request that is Resolved or Closed. Graphite for a request still needing someone (Open, Seen, Being fixed) and for Urgent, printed plain. Carbon for Invited, beside the invitee's name. A charge not yet due reads "Upcoming" in the ledger and "Due 18 Sept" in the register's month column.
- **Filled marks:** square-cornered with 0.5rem side padding: Due today on Canary with a Graphite label, Overdue on Crimson Wash with a Crimson label (on the slip, "Overdue since 5 Sept").
- Paid is not a mark; it is the Stamp. Resolved is only ever a plain mark. The component also carries Occupied (Graphite) and On notice (Soft Graphite) tones that no screen uses today; an occupied row shows its tenant's name instead.

### Stamp
Violet stamp-pad ink in a double ring, set down crooked. It is the only carrier of Stamp Violet, it is lettered "Paid" everywhere, and only a payment Razorpay has confirmed sets it.
- **Mark size:** 0.875rem at 700 in a 3px double ring with 3px corners and 0.5rem side padding, tilted -3 degrees. It sits in the ledger's status column and the register's month column, in the table's condensed width.
- **Slip size:** 1.728rem at 700 (2.074rem from 48rem) and 118% width, in a 4px double ring, tilted -8 degrees, its ink multiplied into the paper (mix-blend-mode: multiply) so the digits show through. It is pressed across the end of the paid slip's display amount: anchored 1.25rem short of the amount's right edge and centred on its height, so the ring overlaps the last digit and every figure stays readable. On a narrow phone a very long amount runs the stamp off the paper's edge, where the slip clips it, rather than widening the page.
- **On paper:** the receipt PDF draws its own, a violet double ring (1.6pt outside, 0.6pt inside) with "Paid" in the wide face, set crooked over the top corner.

**The One Motion Rule.** Motion is spent once: the Paid stamp lands. Over 520ms on the house ease-out (cubic-bezier(0.16, 1, 0.3, 1)) it comes down from its tilt less 9 degrees at 1.9 scale and no opacity, gives on contact to its tilt plus 1 degree at 0.94 scale by 55%, springs to 1.02 by 80%, and rests at its tilt. It plays on the slip only for a receipt that arrives while the slip is open, and in the register only on a row that was showing unpaid rent; a stamp already there when the page opens is simply there, and the ledger's stamps never land. Reduced motion turns the landing off. Apart from it, motion is limited to 150ms colour transitions on controls and tabs and the button's 1px press, which reduced motion collapses too. A loading sheet stays still.

### Sheets
- **Corner Style:** square (0).
- **Background:** Original White for an original, Duplicate Canary for a duplicate.
- **Shadow Strategy:** the sheet shadow, the only one (see Elevation & Depth).
- **Border:** none; the top edge is perforated.
- **Internal Padding:** 2rem / 1.5rem / 1.5rem on phones, 3rem / 2rem / 2rem from 48rem.
- A sheet holds a whole record (the slip, the terms, the tenant's rent book, the receipts, the documents on file, a sign-in form). Its contents are ruled rows and fields, never nested sheets or cards. While a page loads, a blank original sheet (30rem) shows three still Rail bars. A freshly issued invite link sits in a flat Sheet-grey panel (1.5rem padding, no shadow) on the landlord's page.

### Inputs / Fields
- **Style:** Original White inside a 1px Strong Rule border with 4px corners, 44px tall, 0.75rem inline padding. The label sits above at 0.875rem/600 in Graphite; the typed value is Carbon with tabular figures; placeholders and an empty date's dd/mm/yyyy mask are Soft Graphite.
- **Prefix:** a fixed unit (₹, +91) sits in its own Soft Graphite cell behind a 1px Rule divider, so the value stays a pure entry.
- **Text area:** the same field at least 6rem tall (four rows by default, three in a thread's reply), 0.5rem 0.75rem padding, Carbon text at body leading with proportional figures, resizing vertically only.
- **Checkbox:** a native box at 1.125rem with a Carbon accent, its label beside it at 600, the whole label at least 44px tall ("Only I can see this").
- **File choosers:** a label-button (see Buttons) with the chosen filename beside or below it in Carbon, or its hint in Soft Graphite until something is chosen.
- **Focus:** the border turns Carbon and a 2px Carbon ring sits 1px outside it.
- **Error:** the border turns Crimson and a Crimson line (0.875rem, 550) follows beneath. A form-level failure is a Crimson Wash band with a Crimson message at 550 (0.75rem 1rem padding). Hints sit beneath in Soft Graphite at 0.875rem.
- **Groups:** a long form splits into borderless fieldsets whose legends are Title Medium (1.2rem, 650), 2rem apart.
- **Select and date:** a native select redrawn with a two-triangle Soft Graphite chevron; the date picker's glyph redrawn in the icon stroke.
- **Link to copy:** a read-only field on Sheet grey with a dashed Strong Rule border and the link in Carbon at 0.875rem. With the tenant's mobile known, Send on WhatsApp is the primary action (full width on phones) beside a secondary Copy link whose icon turns to a check; without it, Copy link leads and WhatsApp is a text link.

### Navigation
- **Top bar:** the wordmark (a Carbon receipt slip with punched holes and a canary dot, and "Rentbook" at 1.2rem/700, 82% width) on the left. On the right: the landlord's links, Add property (from 64rem, with the plus icon) and Payouts (every size), in Carbon at 0.875rem/600 with a 2px underline at 0.3em when hovered or current; the user's name in Soft Graphite, hidden under 30rem for both roles; and a quiet Sign out with the door icon.
- **Live line:** under each top bar, a status line writes the latest change to the shared book in Carbon at 600, saying who did it. For the landlord: "Asha Rao accepted your invite and has moved in.", "Razorpay confirmed Asha Rao's payment of ₹37,500.", 'Asha reported "Kitchen tap leaking".' or "Asha filed an ID document: aadhaar.pdf." For the tenant: "Lata added Electricity for September, ₹1,800.", "Lata waived Electricity for September.", "Razorpay confirmed your payment of ₹37,500. Receipt 0001 is ready.", 'Lata is having "Kitchen tap leaking" fixed.' or "Lata filed the lease agreement: lease-agreement.pdf." A reply in a thread reads 'Lata wrote on "Kitchen tap leaking".' On the tenant's screens the line aligns to the content column from 48rem.
- **Landlord index tabs:** one per property, labels at 600 and 82% width, truncated with an ellipsis. Unopened tabs are Rail with Graphite labels and lighten on hover; the open tab is Original White with a Carbon label and merges into the page. On phones they form a scrolling strip with 4px top corners and a borderless Add property tab at the end. From 64rem they stack, sticky, down the left edge: right-aligned, up to 13rem wide, outlined in 1px Rule with no right side and 4px left corners, overlapping the page's left rule by 1px so the open tab cuts into it.
- **Tenant tabs:** Home, Rent, Requests and Documents, in the same hand (600, 82% width, Rail when closed, 4px top corners, 1.5rem side padding, 0.75rem below 30rem) along a 1px Rule line on the Sheet ground. The open tab is Sheet grey with a Carbon label, outlined in Rule, its bottom edge joined to the ground below. From 48rem the first tab lines up with the 64rem content column.
- **Back links:** a Carbon link at 600 above a request's thread: "All requests" for the tenant, "Back to Sunrise PG" for the landlord.
- **Links:** Carbon with a 1px underline at 0.2em offset, 2px on hover.

### The Register
The landlord's page for one property this month.
- Condensed (82% width) with tabular figures; fixed columns Unit (7rem), Tenant (the rest), Rent (9rem), the month (8rem) and actions (10rem). Column heads at 0.875rem/600 in Soft Graphite over a 2px Graphite rule; rows ruled with 1px Rule hairlines at 0.75rem cell padding.
- A PG room is a group row (650, with its bed count in Soft Graphite) and its beds are indented 1.5rem. Unit labels are Graphite at 650; tenant names (linking to the lease), rents and due days are Carbon; amounts align right. The month column carries this month's rent: Due today, "Due 18 Sept" while upcoming, Overdue, Waived, or the Paid stamp, which lands when Razorpay's confirmation flips a row that was showing unpaid rent. It shows the due day in Carbon when nothing is billed yet, and "link expires ..." in Soft Graphite small type for a pending invite. A vacant row shows "asking ₹..." in Soft Graphite small type and a quiet Invite a tenant.
- The total closes the table under a 3px double Graphite rule: "Collected in September" with its tally ("1 of 5 let, 4 vacant") in Soft Graphite at 500, and the collected sum in Carbon at 1.2rem, followed by "of ₹12,500" in Soft Graphite small type.

### The Ledger
The rent book itself: every charge on one lease, the same rows for both roles. The landlord reads it on the lease page under "Rent book"; the tenant reads it on the Rent tab, on a perforated original sheet.
- The register's discipline: fixed columns Due (8rem), Amount (9rem), Status (8rem) and, on the landlord's copy, actions (9rem); For takes the rest, within a 52rem measure. Due dates and amounts are Carbon, what the line is for is Graphite, and amounts align right, all in the register's condensed tabular type. A spare header cell kept only for alignment carries the same 2px rule.
- Each line carries one status: Upcoming, Due today, Overdue, Waived, or the Paid stamp. The landlord may waive any open line (upcoming, due today or overdue). Waive asks on a line of its own under the entry, starting where For starts: the question at 600 ("Waive Electricity for September?"), then Waive it and Keep it. Focus moves to the question, Escape keeps the charge, and the entry's hairline is held back so the two read as one line of the book.
- The total closes it under a 3px double Graphite rule: "Outstanding" and the sum in Carbon at 1.2rem, with "₹... of it overdue" beneath the label in Crimson at 0.875rem/600 when any is late.
- Empty, it prints one Soft Graphite line under a 2px Graphite rule spanning the 52rem measure: "Nothing billed yet. Rent appears here ten days before it is due."
- Above it on the lease page, the terms (Rent, Due on, Deposit, Moved in, Lease ends) are printed across one band between two Rule hairlines: Soft Graphite terms at 0.875rem/600 over Carbon values in Entry Large, wrapping to more lines on narrow screens.

**The Struck Line Rule.** A waived line stays in the book: its description and amount are struck through (1px) in Soft Graphite and it carries a plain Waived mark. A charge that happened is never removed from either reader's view.

### Receipts
Every receipt on a lease, ruled like the ledger whose lines it settles, and read by both roles.
- The ledger's table, measure and column widths: Issued (8rem, the date in Carbon), Receipt ("Receipt 0001", the number in Carbon, with what it settled beneath in Soft Graphite small type), Amount (9rem, Carbon), and a quiet Download row action.
- On the tenant's Rent page the receipts sit on a second perforated original sheet headed "Receipts" (1.2rem, 650), with Download in the status column. On the landlord's lease page Download moves to the actions column, so it lines up under the ledger's Waive links.
- Empty, it prints "No receipts yet. Each payment Razorpay confirms gets one here." in Soft Graphite under the ledger's full-measure 2px rule.

### Requests
A maintenance request is a page of the book both people write on.
- **The request list:** shared by the tenant's Requests page and the landlord's "Needs you". Ruled lines under a 2px Graphite rule, 52rem wide: the title as a Carbon link at 600, the unit with the tenant (for the landlord) or the property (for the tenant) beneath in Soft Graphite small type, then Urgent if it applies, the status mark, and the last-activity date in Soft Graphite small tabular type. Empty, it prints "Nothing reported yet." under the 2px rule.
- **Needs you:** a Title Large section under the register, shown only while requests are open, urgent first and otherwise most recently active first. In the first view, a Carbon link at 600 under the property's address ("1 request needs you") scrolls down to it.
- **The thread:** the title in Headline Large, a where-line in Soft Graphite ("Bed A, Room 201, Sunrise PG. Reported by Asha Rao on 11 Sept 2026."), and a marks row: status, Urgent, then the category in Soft Graphite small type. The lines follow, oldest first, under a 2px Graphite rule and ruled in Rule hairlines. A message has a byline (the author in Graphite at 0.875rem/600, the time in Soft Graphite at 0.875rem, tabular, in IST: "11 Sept, 7:43 pm"), then the words in Carbon, keeping their line breaks within 68ch, then its photos as prints. A status change is a line of its own in Graphite at 0.875rem/600 ("Lata is having it fixed."), its time at 400.
- **Moving it on:** secondary buttons, offered only for the reader's allowed next statuses: Mark as seen, Mark being fixed, Mark resolved, Close request, Reopen. Writing stays the thread's one primary.
- **Reply:** a text area, "Add to the thread", the photo picker, and Send. A closed request shows a Soft Graphite note in its place.
- **Report a problem:** the tenant's Requests page leads with a Headline Medium heading, a lede and the primary Report a problem. The form is 36rem, starting at the tenant column's left edge: What needs fixing; What kind of problem and How soon, side by side from 30rem; Describe it, a text area with a hint; photos; and the primary "Send to Lata".

### Prints and the Photo Picker
- **Prints:** every photo in a thread or waiting to be sent is a print (see Shapes), with the sheet shadow. In a thread each opens the full photo.
- **Pending photos:** each print carries a line beneath it, "Uploading" or "Ready" in Soft Graphite at 0.875rem, or the failure in Crimson at 600, and a quiet Remove; a print still uploading shows at 55% opacity.
- **Attach photos:** a secondary label-button with the plus icon, above the Soft Graphite hint "Up to six photos, 10 MB each." It disappears once six are attached.

### The Vault
The documents on a lease, kept on one shelf both parties read.
- **Shelf lines:** ruled lines under a 2px Graphite rule, 52rem wide. Each prints the type in Graphite at 0.875rem/600 ("Lease agreement", "ID document", "Other", adding ", only you can see it" to a landlord's private file), the filename in Carbon, and "Filed by you (or Lata Iyer) on 11 Sept 2026, 2 KB" in Soft Graphite small type. Every line has a quiet Download; your own files add a quiet Remove, which asks inline at 600: "Take aadhaar.pdf off the shelf?", then Remove it and Keep it. Empty, it prints "Nothing filed yet."
- **Placement:** on the tenant's Documents tab, a Headline Medium heading and lede, then a perforated original sheet headed "On file" (Title Medium) holding the shelf, then a "File a document" section (Title Large). On the landlord's lease page, a "Documents" section comes last, after "Add a charge".
- **Filing:** a "What it is" select whose options depend on the role, beside a "Choose a file" label-button (side by side from 40rem) with the chosen filename in Carbon or the hint "A PDF or a photo, up to 10 MB."; for a landlord's Other file, the "Only I can see this" checkbox; then File it, primary on the tenant's page and secondary on the lease page, with a "Filed aadhaar.pdf." status line in Carbon at 600.

### The Rent Slip
The tenant's first sight: everything owed, as one bill.
- The slip is canary while any charge on it is due today or overdue, and a white original when everything owed is still upcoming; both are perforated along the top and clip their own overflow.
- Its heading (1.2rem, 600) is the charge's description when there is one charge, "Due now" when several fall due on one date, "Outstanding" when their dates differ, and "Coming up" when they share a date that has not arrived. Beneath it sit the total in the display style, then "Due" (or "First due") with the date in Carbon at 1.2rem, or the Overdue mark.
- With more than one charge, the bill lines follow, ruled in Graphite at 22%: each description with its own due date beneath in small Carbon ("due 18 Sept", "overdue since 5 Sept"), and its amount in Carbon. The last line takes no hairline, because the dashed tear line (1px Graphite at 35%) closes the bill.
- Below the tear, one status line (role=status throughout, so each change is announced) and, while payment is possible, the full-width Pay bar:
  - **Pay:** the Carbon bar "Pay ₹37,500" over "Through Razorpay, in test mode: no real money moves. Your receipt comes once the bank confirms." at 0.875rem.
  - **Not yet online:** when the landlord has no active payouts, the bar is disabled in Rule grey and the note says to pay the landlord the way the tenant does now.
  - **Waiting:** the bar gives way to the status line alone, in Graphite at 600 with the amount in Carbon: "Payment sent. Waiting for the bank to confirm ₹37,500. This slip changes by itself when it does." The slip stays canary and carries no stamp.
  - **Declined or failed:** a Crimson line at 0.875rem/600 above the note.
- **Paid:** a white slip headed "All paid up" (1.2rem, 600), the amount the receipt settled in the display style with the slip stamp pressed across its end, "Received on 11 Sept 2026, confirmed by Razorpay." at 1.2rem with the date in Carbon, the next rent and its date, then a secondary "Download receipt 0001".
- **Paid up with no receipt:** a white slip, "All paid up" at 1.728rem, with the next rent and its date in Carbon.
- "Your home" follows on an original sheet: a ruled definition list with a 6.5rem term column, Graphite terms at 0.875rem/600, Carbon values, Call and Email links with their icons, and a Crimson line if the lease is on notice.

### The Receipt PDF
The paper a tenant keeps for an HRA claim, in the app's own hands.
- A5, set in Anek from static instances embedded in the file (SIL OFL): Regular and SemiBold at 100% width for text, headings and the total's label, WideBold at 118% for amounts and the stamp. ₹ comes from the same Anek Devanagari subset the app uses, and amounts are grouped the Indian way.
- Labels in Soft Graphite, entries in Carbon, running text in Graphite. The items are ruled with Rule-grey hairlines and closed by a 1.5pt Graphite rule over the total.
- It prints the amount in words, the home's full address with its PIN, the landlord's name and PAN, and a footer proof line in Soft Graphite naming the Razorpay payment id and the moment its signed notification arrived, with the violet Paid stamp over the top corner.

### Payouts
Where a landlord connects a bank account for online rent, within a 36rem measure.
- The heading is Headline Large: "Get paid online" until the linked account is active, "Online rent is on" after. A Soft Graphite lede names the platform fee and that Razorpay runs in test mode.
- The form sets its fields in two fieldsets, "About you" and "Where your rent goes", under Title Medium legends. A Mobile number field with the +91 prefix appears only when the account has no phone, the account number is asked for twice, and Set up payouts is the primary.
- Once active, the account is printed as a ruled definition list under a 2px Graphite rule: an 8rem column of Graphite terms at 0.875rem/600, with values in Carbon.

### The Receipt-Book Spread
The invite page is one spread: the landlord writes the terms on the white original, and the canary duplicate joined to it along a perforated tear fills in live with what the tenant will receive. The pair shares one sheet shadow; the duplicate's list is ruled in Graphite at 22%, with a 6.5rem term column and values in the Entry Large style. Stacked on phones, the duplicate's top edge is the tear; from 60rem it sits beside the original (1.2 to 0.8) and is perforated down the left edge it tore from as well as along its top. The tenant later receives the same canary duplicate on the accept page, beside the original sheet that holds the accept form.

## Do's and Don'ts

### Do:
- **Do** write every entered value (amounts, dates, names, filenames, the words of a message, typed text) in Carbon (#2a3190) and print every label and heading in Graphite (#1f2329) or Soft Graphite (#4a515c).
- **Do** set figures in data in tabular figures, render ₹ from the 'Anek Rupee' subset, and group amounts the Indian way (₹1,84,000), on screen and on the receipt PDF.
- **Do** change Anek Latin's width to change voice: 82% for registers, ledgers, tabs and the wordmark, 100% for the interface, 118% for the one amount and the stamp.
- **Do** keep sheets square with a perforated top edge whose holes take the colour of the ground beneath.
- **Do** pin photos in as prints: square, cropped to fill, a 4px Original White border and the sheet shadow.
- **Do** build lists as ruled rows: 1px Rule (#c9ced6) hairlines, a 2px Graphite rule under the heads or over the list, a 3px double Graphite rule over a total, fixed columns in tables, and two-line rows under 40rem.
- **Do** say a charge that is not yet due quietly, with the Soft Graphite Upcoming mark, and keep Duplicate Canary (#f2da55) for what must be paid today.
- **Do** list urgent requests first and mark them Urgent in plain Graphite; let the order carry the priority.
- **Do** keep a waived line in the ledger, struck through in Soft Graphite (#4a515c), rather than removing it.
- **Do** set the Paid stamp only on a payment the server has verified: at mark size in tables, at slip size across the amount it settled, and landing only when the confirmation arrives while someone is looking.
- **Do** say plainly, in print and without violet, that a payment is waiting for the bank, until the bank answers.
- **Do** tell each reader, in a Carbon live line, who changed the shared book and what they changed.
- **Do** keep each view to one Carbon primary; when a page already has one, make any other forward action secondary.
- **Do** keep every control at least 44px (2.75rem) tall, or give a small row action an invisible 44px hit area so its row keeps one pitch.
- **Do** give the blocks of one page a shared measure: 52rem on the landlord's pages and for every ledger, receipts table, request list, thread and shelf; a 64rem content column for the tenant, with every tenant page starting at its left edge.
- **Do** show keyboard focus with the 2px Carbon ring, 2px outside a control and 1px outside a field's border.

### Don't:
- **Don't** use Stamp Violet (#6b3a9e) for anything the server has not verified: not for emphasis, links, decoration, a tenant's claim of payment or a payment still waiting for the bank.
- **Don't** carry Stamp Violet on anything but the Stamp, or letter the stamp anything but "Paid".
- **Don't** stamp Resolved, or any status a person sets; it is the landlord's word, not a verified fact.
- **Don't** use Duplicate Canary (#f2da55) as a general highlight or hover, or for a charge that is not yet due; outside the wordmark it means the duplicate copy or what must be paid now.
- **Don't** use Overdue Crimson (#a3203a) for urgency; urgency is order and a plain Graphite mark.
- **Don't** use Overdue Crimson (#a3203a) for anything but lateness, errors and the lease-on-notice warning.
- **Don't** cast a shadow from anything but paper (sheets and photo prints), and don't lift anything on hover.
- **Don't** put rows into their own cards or nest sheets inside sheets.
- **Don't** confirm a row action in a modal; ask inline, in the book.
- **Don't** give any surface or control a radius above 6px, or round a sheet or a print at all.
- **Don't** animate entrances or add decorative motion; the landing Paid stamp is the one authored motion.
- **Don't** let a landing stamp widen the page; its containers clip horizontal overflow.
- **Don't** use a warm cream ground with a terracotta or clay accent, or a near-black ground with one neon accent; the ground is cool Sheet grey (#f3f5f7).
- **Don't** use tracked-out all-caps eyebrow labels, meta text joined with middle dots, em-dash labels, or arrows tacked onto buttons and links.
- **Don't** use gradient washes as decoration; here gradients only draw things (the perforation holes, the select chevron).
- **Don't** set text in any face outside the Anek family.
