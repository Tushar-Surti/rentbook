-- Settling the security deposit when a tenant moves out: the landlord proposes deductions, the tenant
-- accepts or asks about them, and the refund is recorded. A deduction may clear an unpaid charge; on
-- acceptance that charge is paid from the deposit, with a receipt, as a payment of method DEPOSIT.
create table deposit_settlements (
    id                uuid primary key,
    lease_id          uuid         not null unique references leases (id),
    landlord_id       uuid         not null references users (id),
    tenant_id         uuid         not null references users (id),
    held_paise        bigint       not null check (held_paise >= 0),
    status            varchar(16)  not null check (status in ('PROPOSED', 'QUERIED', 'ACCEPTED', 'SETTLED')),
    landlord_note     varchar(500),
    tenant_note       varchar(500),
    proposed_at       timestamptz  not null,
    responded_at      timestamptz,
    refund_method     varchar(16)  check (refund_method in ('CASH', 'UPI', 'BANK_TRANSFER', 'CHEQUE')),
    refunded_on       date,
    refund_reference  varchar(200),
    settled_at        timestamptz,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    version           bigint       not null default 0
);

create index deposit_settlements_landlord on deposit_settlements (landlord_id, status);

create table deposit_deductions (
    settlement_id  uuid          not null references deposit_settlements (id) on delete cascade,
    position       integer       not null,
    description    varchar(160)  not null,
    amount_paise   bigint        not null check (amount_paise > 0),
    charge_id      uuid          references charges (id),
    primary key (settlement_id, position)
);

-- Payments may now be settled from the deposit.
do $$
declare
    existing text;
begin
    select conname into existing from pg_constraint
    where conrelid = 'payments'::regclass and contype = 'c' and pg_get_constraintdef(oid) like '%RAZORPAY%';
    execute format('alter table payments drop constraint %I', existing);
end $$;

alter table payments add constraint payments_method_check
    check (method in ('RAZORPAY', 'CASH', 'UPI', 'BANK_TRANSFER', 'CHEQUE', 'DEPOSIT'));
