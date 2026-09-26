// Mirrors the backend's JSON. Dates are ISO strings: LocalDate as "2026-10-05", Instant as full timestamps.

export type Role = 'LANDLORD' | 'TENANT'

export type Me = {
  id: string
  role: Role
  fullName: string
  email: string
  phone: string | null
}

export type SessionResponse = {
  accessToken: string
  accessTokenExpiresAt: string
  user: Me
}

export type Problem = {
  status?: number
  title?: string
  detail?: string
  code?: string
  errors?: Record<string, string>
}

export type PropertyKind = 'PG' | 'APARTMENT' | 'HOUSE'
export type UnitKind = 'FLAT' | 'ROOM' | 'BED'
export type UnitStatus = 'VACANT' | 'OCCUPIED' | 'INACTIVE'

export type Unit = {
  id: string
  parentUnitId: string | null
  kind: UnitKind
  label: string
  defaultRentPaise: number | null
  defaultDepositPaise: number | null
  status: UnitStatus
}

export type Property = {
  id: string
  name: string
  kind: PropertyKind
  addressLine: string
  city: string
  pincode: string
  units: Unit[]
}

export type InviteStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED'

export type Invite = {
  id: string
  unitId: string
  unitLabel: string
  propertyName: string
  tenantName: string
  email: string
  phone: string | null
  rentPaise: number
  depositPaise: number
  dueDay: number
  startsOn: string
  endsOn: string | null
  status: InviteStatus
  expiresAt: string
}

export type IssuedInvite = { invite: Invite; link: string }

export type InvitePreview = {
  tenantName: string
  email: string
  landlordName: string
  propertyName: string
  city: string
  unitKind: UnitKind
  unitLabel: string
  roomLabel: string | null
  rentPaise: number
  depositPaise: number
  dueDay: number
  startsOn: string
  endsOn: string | null
  expiresAt: string
  existingAccount: boolean
}

export type LeaseStatus = 'ACTIVE' | 'NOTICE' | 'ENDED'

export type PersonRef = { id: string; fullName: string; email: string; phone: string | null }

export type LeaseView = {
  id: string
  status: LeaseStatus
  rentPaise: number
  depositPaise: number
  dueDay: number
  startsOn: string
  endsOn: string | null
  unit: { id: string; kind: UnitKind; label: string; roomLabel: string | null }
  property: { id: string; name: string; city: string }
  landlord: PersonRef
  tenant: PersonRef
}

export type ChargeKind = 'RENT' | 'DEPOSIT' | 'UTILITY' | 'OTHER'

/** UPCOMING is not due yet, DUE falls due today, OVERDUE is past its date; only a verified payment makes PAID. */
export type EntryStatus = 'UPCOMING' | 'DUE' | 'OVERDUE' | 'PAID' | 'WAIVED'

/** A ledger change as pushed on /topic/leases/{id}; actor is null for rent the system generated. */
export type LedgerEvent = { leaseId: string; description: string; amountPaise: number; actor: string | null }

export type LedgerEntry = {
  id: string
  kind: ChargeKind
  description: string
  periodMonth: string | null
  dueOn: string
  amountPaise: number
  status: EntryStatus
  paidAt: string | null
  waivedAt: string | null
}

export type Ledger = { leaseId: string; outstandingPaise: number; overduePaise: number; entries: LedgerEntry[] }

export type MonthRent = { status: EntryStatus; amountPaise: number; dueOn: string }

export type Hook = {
  unitId: string
  kind: UnitKind
  label: string
  roomId: string | null
  roomLabel: string | null
  status: UnitStatus
  occupant: {
    leaseId: string
    tenantId: string
    tenantName: string
    rentPaise: number
    dueDay: number
    thisMonth: MonthRent | null
    outstandingPaise: number
  } | null
  invite: { inviteId: string; tenantName: string; expiresAt: string } | null
}

export type PropertyBoard = { id: string; name: string; kind: PropertyKind; city: string; hooks: Hook[] }

export type LandlordBoard = {
  /** The month the register is for, as "2026-09". */
  month: string
  totals: { units: number; occupied: number; vacant: number; openInvites: number; monthlyRentPaise: number }
  properties: PropertyBoard[]
}

export type ChargeLine = {
  id: string
  kind: ChargeKind
  description: string
  dueOn: string
  amountPaise: number
  status: EntryStatus
}

export type TenantHome = {
  lease: LeaseView | null
  outstanding: ChargeLine[]
  outstandingPaise: number
  overdue: boolean
  nextDueOn: string | null
  nextRentPaise: number
  /** False until the landlord's Razorpay payout account is active. */
  payOnline: boolean
  /** A payment the browser reported as made that Razorpay has not confirmed yet. */
  pendingPayment: PendingPayment | null
  lastReceipt: Receipt | null
}

export type PendingPayment = { id: string; amountPaise: number }

export type PaymentStatus = 'CREATED' | 'AWAITING_WEBHOOK' | 'CAPTURED' | 'FAILED'

export type PaymentView = {
  id: string
  status: PaymentStatus
  amountPaise: number
  capturedAt: string | null
  failureReason: string | null
}

/** What Razorpay Checkout needs to open for an order the server created. */
export type Checkout = {
  paymentId: string
  orderId: string
  keyId: string
  amountPaise: number
  currency: string
  description: string
  prefill: { name: string; email: string; contact: string | null }
}

export type PaymentMethod = 'RAZORPAY' | 'CASH' | 'UPI' | 'BANK_TRANSFER' | 'CHEQUE'

export type Receipt = {
  id: string
  /** Numbered per landlord, as printed: "0001". */
  number: string
  issuedAt: string
  amountPaise: number
  /** Razorpay's payment id; null when the landlord recorded the payment. */
  paymentReference: string | null
  items: string[]
  method: PaymentMethod
  /** The day the landlord received it, for a payment they recorded. */
  receivedOn: string | null
}

export type RecordedPayment = { paymentId: string; receiptId: string; receiptNumber: string; amountPaise: number }

/** Pushed on /topic/leases/{id} to both parties; the landlord's own queue adds the tenant's name. */
export type PaymentEvent = {
  leaseId: string
  paymentId: string
  receiptId: string
  receiptNumber: string
  amountPaise: number
  method?: PaymentMethod
  tenantName?: string
}

export type Payouts = {
  /** False when the server has no Razorpay keys, so nobody can be paid online. */
  paymentsConfigured: boolean
  status: string
  active: boolean
  legalName: string | null
  beneficiaryName: string | null
  ifsc: string | null
  bankLast4: string | null
  platformFeeBps: number
}

export type TicketCategory =
  | 'PLUMBING'
  | 'ELECTRICAL'
  | 'APPLIANCE'
  | 'FURNITURE'
  | 'CLEANING'
  | 'PESTS'
  | 'INTERNET'
  | 'OTHER'
export type TicketPriority = 'LOW' | 'NORMAL' | 'URGENT'
export type TicketStatus = 'OPEN' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'

/** One maintenance request; both parties read the same row. */
export type TicketView = {
  id: string
  leaseId: string
  title: string
  category: TicketCategory
  priority: TicketPriority
  status: TicketStatus
  openedAt: string
  lastActivityAt: string
  unit: { id: string; kind: UnitKind; label: string; roomLabel: string | null }
  property: { id: string; name: string; city: string }
  tenant: PersonRef
  landlord: PersonRef
}

/** A photo attached to a message; the url is a short-lived signed link straight to storage. */
export type Photo = { id: string; filename: string; url: string }

export type TicketEvent = {
  id: string
  kind: 'MESSAGE' | 'STATUS_CHANGE'
  body: string | null
  fromStatus: TicketStatus | null
  toStatus: TicketStatus | null
  at: string
  author: { id: string; name: string; role: Role }
  photos: Photo[]
}

/** The thread both parties write on, and the statuses this reader may move it to next. */
export type TicketThread = { ticket: TicketView; events: TicketEvent[]; nextStatuses: TicketStatus[] }

/** Pushed on /topic/tickets/{id} to both parties, and to the other party's own queue. */
export type TicketPush = {
  ticketId: string
  leaseId: string
  title: string
  kind: 'opened' | 'message' | 'status'
  actor: string
  status: TicketStatus
}

export type DocumentType = 'LEASE' | 'KYC' | 'RECEIPT' | 'TICKET_PHOTO' | 'OTHER'
export type DocumentVisibility = 'LANDLORD_ONLY' | 'LEASE_PARTIES'

/** A file on a lease's shelf in the vault; `mine` when this reader filed it (and so may remove it). */
export type VaultEntry = {
  id: string
  type: DocumentType
  visibility: DocumentVisibility
  filename: string
  contentType: string
  sizeBytes: number | null
  uploadedAt: string
  uploadedBy: string
  mine: boolean
}

/** Pushed to the lease's topic and to the other party's queue when a shared document is filed. */
export type DocumentPush = { leaseId: string; documentId: string; type: DocumentType; filename: string; actor: string }

/** Where and how the browser sends a file: straight to storage, never through the API. */
export type UploadTicket = {
  documentId: string
  url: string
  method: 'PUT'
  headers: Record<string, string>
  expiresAt: string
}

export type LiveEnvelope<T = unknown> = { type: string; at: string; data: T }
