-- Razorpay Route: landlord linked accounts, payments with their split, receipts, and the webhook log.

-- Each landlord settles to their own Route linked account. Only the last four digits of the bank
-- account are kept; the full number goes to Razorpay and nowhere else.
create table payout_accounts (
    landlord_id         uuid primary key references users (id),
    rzp_account_id      varchar(40),
    rzp_stakeholder_id  varchar(40),
    rzp_product_id      varchar(40),
    activation_status   varchar(32)  not null default 'NOT_STARTED',
    legal_name          varchar(200) not null,
    beneficiary_name    varchar(120) not null,
    ifsc                varchar(11)  not null,
    bank_last4          varchar(4)   not null,
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    version             bigint       not null default 0
);

-- One checkout. The amount splits into the landlord's share (transferred by Route) and the platform fee.
create table payments (
    id                    uuid primary key,
    lease_id              uuid        not null references leases (id),
    tenant_id             uuid        not null references users (id),
    landlord_id           uuid        not null references users (id),
    amount_paise          bigint      not null check (amount_paise > 0),
    platform_fee_paise    bigint      not null check (platform_fee_paise >= 0),
    landlord_share_paise  bigint      not null check (landlord_share_paise > 0),
    rzp_order_id          varchar(40) unique,
    rzp_payment_id        varchar(40) unique,
    rzp_transfer_id       varchar(40),
    status                varchar(24) not null
                              check (status in ('CREATED', 'AWAITING_WEBHOOK', 'CAPTURED', 'FAILED')),
    transfer_status       varchar(24),
    failure_reason        varchar(300),
    captured_at           timestamptz,
    created_at            timestamptz not null,
    updated_at            timestamptz not null,
    version               bigint      not null default 0,
    check (platform_fee_paise + landlord_share_paise = amount_paise)
);
create index payments_lease_idx on payments (lease_id, created_at);

create table payment_charges (
    payment_id  uuid not null references payments (id),
    charge_id   uuid not null references charges (id),
    primary key (payment_id, charge_id)
);
create index payment_charges_charge_idx on payment_charges (charge_id);

-- Receipts are numbered per landlord, in the order payments were confirmed.
alter table landlord_profiles add column receipt_seq integer not null default 0;

create table receipts (
    id           uuid primary key,
    payment_id   uuid        not null unique references payments (id),
    landlord_id  uuid        not null references users (id),
    serial_no    integer     not null,
    issued_at    timestamptz not null,
    unique (landlord_id, serial_no)
);

-- Every webhook delivery, keyed by Razorpay's event id so a retried delivery is processed once.
create table webhook_events (
    id            uuid primary key,
    event_id      varchar(80)  not null unique,
    event_type    varchar(60)  not null,
    payload       jsonb        not null,
    status        varchar(16)  not null check (status in ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED')),
    error         varchar(500),
    received_at   timestamptz  not null,
    processed_at  timestamptz
);
