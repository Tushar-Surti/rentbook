-- The shared rent ledger, and the log that keeps reminders from going out twice.

-- What a tenant owes. Rent is generated month by month; deposits and extra charges are one-offs.
-- A payment (next migration) settles charges; nothing here is ever deleted, only waived or paid.
create table charges (
    id            uuid primary key,
    lease_id      uuid         not null references leases (id),
    kind          varchar(16)  not null check (kind in ('RENT', 'DEPOSIT', 'UTILITY', 'OTHER')),
    period_month  date,
    description   varchar(160) not null,
    amount_paise  bigint       not null check (amount_paise > 0),
    due_on        date         not null,
    status        varchar(16)  not null default 'DUE' check (status in ('DUE', 'PAID', 'WAIVED')),
    paid_at       timestamptz,
    waived_at     timestamptz,
    waived_by     uuid references users (id),
    created_by    uuid references users (id),
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    version       bigint       not null default 0,
    check ((kind = 'RENT') = (period_month is not null)),
    check (period_month is null or extract(day from period_month) = 1)
);
-- These keep monthly generation idempotent, even with two instances running the job at once.
create unique index charges_one_rent_per_month on charges (lease_id, period_month) where kind = 'RENT';
create unique index charges_one_deposit_per_lease on charges (lease_id) where kind = 'DEPOSIT';
create index charges_lease_idx on charges (lease_id, due_on);
create index charges_open_idx on charges (due_on) where status = 'DUE';

-- One row per message the system tried to send. The dedupe key is claimed before sending.
create table notifications (
    id           uuid primary key,
    user_id      uuid         not null references users (id),
    channel      varchar(8)   not null check (channel in ('EMAIL', 'SMS')),
    template     varchar(40)  not null,
    dedupe_key   varchar(200) not null unique,
    status       varchar(16)  not null check (status in ('PENDING', 'SENT', 'FAILED')),
    provider_id  varchar(120),
    error        varchar(500),
    created_at   timestamptz  not null,
    sent_at      timestamptz
);
create index notifications_user_idx on notifications (user_id, created_at);
