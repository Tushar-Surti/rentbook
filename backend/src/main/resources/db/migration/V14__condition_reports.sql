-- The walk-through at move-in and at move-out: each room's fittings, their condition, notes and photos.
-- The landlord writes it, the tenant adds their own notes and confirms it, and then it is kept as it was.
create table condition_reports (
    id            uuid primary key,
    lease_id      uuid          not null references leases (id),
    kind          varchar(16)   not null check (kind in ('MOVE_IN', 'MOVE_OUT')),
    status        varchar(16)   not null check (status in ('DRAFT', 'SENT', 'CONFIRMED')),
    sent_at       timestamptz,
    confirmed_at  timestamptz,
    created_at    timestamptz   not null,
    updated_at    timestamptz   not null,
    version       bigint        not null default 0
);

create unique index condition_reports_one_per_kind on condition_reports (lease_id, kind);

create table condition_items (
    id           uuid primary key,
    report_id    uuid          not null references condition_reports (id),
    position     integer       not null,
    area         varchar(60)   not null,
    item         varchar(80)   not null,
    condition    varchar(16)   not null check (condition in ('GOOD', 'WORN', 'DAMAGED', 'MISSING')),
    note         varchar(500),
    tenant_note  varchar(500),
    created_at   timestamptz   not null,
    updated_at   timestamptz   not null,
    version      bigint        not null default 0
);

create index condition_items_report on condition_items (report_id, position);

-- Photos of a line on the report live with the other files, kept off the lease's document shelf.
alter table documents
    add column condition_item_id uuid references condition_items (id),
    drop constraint documents_type_check,
    add constraint documents_type_check
        check (type in ('LEASE', 'KYC', 'RECEIPT', 'TICKET_PHOTO', 'CONDITION_PHOTO', 'OTHER'));

create index documents_condition_item on documents (condition_item_id) where condition_item_id is not null;
