-- Maintenance: one thread per request, written by both parties of the lease and never edited.
create table tickets (
    id                uuid primary key,
    lease_id          uuid         not null references leases (id),
    unit_id           uuid         not null references units (id),
    landlord_id       uuid         not null references users (id),
    tenant_id         uuid         not null references users (id),
    title             varchar(140) not null,
    category          varchar(16)  not null check (category in
                          ('PLUMBING', 'ELECTRICAL', 'APPLIANCE', 'FURNITURE', 'CLEANING', 'PESTS', 'INTERNET', 'OTHER')),
    priority          varchar(8)   not null check (priority in ('LOW', 'NORMAL', 'URGENT')),
    status            varchar(16)  not null check (status in ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    last_activity_at  timestamptz  not null,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    version           bigint       not null default 0
);

create index tickets_landlord_status on tickets (landlord_id, status, last_activity_at desc);
create index tickets_tenant on tickets (tenant_id, last_activity_at desc);

create table ticket_events (
    id           uuid primary key,
    ticket_id    uuid          not null references tickets (id),
    author_id    uuid          not null references users (id),
    kind         varchar(16)   not null check (kind in ('MESSAGE', 'STATUS_CHANGE')),
    body         varchar(4000),
    from_status  varchar(16),
    to_status    varchar(16),
    created_at   timestamptz   not null,
    check ((kind = 'MESSAGE' and body is not null) or (kind = 'STATUS_CHANGE' and to_status is not null))
);

create index ticket_events_thread on ticket_events (ticket_id, created_at);

-- Files in S3-compatible storage: maintenance photos now, the document vault next. Rows start PENDING
-- when an upload URL is issued and become AVAILABLE once the object is confirmed in the bucket.
create table documents (
    id               uuid primary key,
    landlord_id      uuid          not null references users (id),
    lease_id         uuid          references leases (id),
    ticket_event_id  uuid          references ticket_events (id),
    uploaded_by      uuid          not null references users (id),
    type             varchar(16)   not null check (type in ('LEASE', 'KYC', 'RECEIPT', 'TICKET_PHOTO', 'OTHER')),
    visibility       varchar(16)   not null check (visibility in ('LANDLORD_ONLY', 'LEASE_PARTIES')),
    storage_key      varchar(300)  not null unique,
    filename         varchar(200)  not null,
    content_type     varchar(100)  not null,
    size_bytes       bigint,
    status           varchar(16)   not null check (status in ('PENDING', 'AVAILABLE')),
    created_at       timestamptz   not null,
    updated_at       timestamptz   not null,
    version          bigint        not null default 0
);

create index documents_lease on documents (lease_id, created_at desc);
create index documents_event on documents (ticket_event_id);
