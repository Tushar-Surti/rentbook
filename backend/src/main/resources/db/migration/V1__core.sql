-- Identity, portfolio, invites and leases.
-- Money is stored in paise (bigint). Timestamps are UTC (timestamptz).

create table users (
    id              uuid primary key,
    role            varchar(16)  not null check (role in ('LANDLORD', 'TENANT')),
    full_name       varchar(120) not null,
    email           varchar(254) not null,
    phone           varchar(16),
    password_hash   varchar(100) not null,
    status          varchar(16)  not null default 'ACTIVE' check (status in ('ACTIVE', 'DISABLED')),
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,
    version         bigint       not null default 0
);
create unique index users_email_uq on users (lower(email));

create table landlord_profiles (
    user_id          uuid primary key references users (id) on delete cascade,
    display_name     varchar(120) not null,
    pan              varchar(10),
    platform_fee_bps integer      not null default 200 check (platform_fee_bps between 0 and 5000),
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    version          bigint       not null default 0
);

create table refresh_tokens (
    id              uuid primary key,
    user_id         uuid        not null references users (id) on delete cascade,
    token_hash      varchar(64) not null unique,
    family_id       uuid        not null,
    expires_at      timestamptz not null,
    revoked_at      timestamptz,
    replaced_by_id  uuid references refresh_tokens (id),
    created_at      timestamptz not null
);
create index refresh_tokens_family_idx on refresh_tokens (family_id);
create index refresh_tokens_user_idx on refresh_tokens (user_id);

create table properties (
    id            uuid primary key,
    landlord_id   uuid         not null references users (id),
    name          varchar(120) not null,
    kind          varchar(16)  not null check (kind in ('PG', 'APARTMENT', 'HOUSE')),
    address_line  varchar(240) not null,
    city          varchar(80)  not null,
    pincode       varchar(6)   not null check (pincode ~ '^[1-9][0-9]{5}$'),
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    version       bigint       not null default 0
);
create index properties_landlord_idx on properties (landlord_id);

-- A unit is a whole flat, a room, or a bed. Beds always sit inside a room.
create table units (
    id                     uuid primary key,
    property_id            uuid        not null references properties (id) on delete cascade,
    parent_unit_id         uuid references units (id),
    kind                   varchar(8)  not null check (kind in ('FLAT', 'ROOM', 'BED')),
    label                  varchar(60) not null,
    default_rent_paise     bigint check (default_rent_paise >= 0),
    default_deposit_paise  bigint check (default_deposit_paise >= 0),
    status                 varchar(16) not null default 'VACANT' check (status in ('VACANT', 'OCCUPIED', 'INACTIVE')),
    created_at             timestamptz not null,
    updated_at             timestamptz not null,
    version                bigint      not null default 0,
    check ((kind = 'BED') = (parent_unit_id is not null))
);
create index units_property_idx on units (property_id);
create index units_parent_idx on units (parent_unit_id);
create unique index units_label_uq
    on units (property_id, coalesce(parent_unit_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(label));

create table invites (
    id                uuid primary key,
    landlord_id       uuid         not null references users (id),
    unit_id           uuid         not null references units (id),
    tenant_name       varchar(120) not null,
    email             varchar(254) not null,
    phone             varchar(16),
    token_hash        varchar(64)  not null unique,
    rent_paise        bigint       not null check (rent_paise > 0),
    deposit_paise     bigint       not null default 0 check (deposit_paise >= 0),
    due_day           integer      not null check (due_day between 1 and 28),
    starts_on         date         not null,
    ends_on           date,
    status            varchar(16)  not null default 'PENDING'
                          check (status in ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    expires_at        timestamptz  not null,
    accepted_user_id  uuid references users (id),
    accepted_at       timestamptz,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    version           bigint       not null default 0,
    check (ends_on is null or ends_on > starts_on)
);
create unique index invites_one_pending_per_unit on invites (unit_id) where status = 'PENDING';
create index invites_landlord_idx on invites (landlord_id);

create table leases (
    id             uuid primary key,
    unit_id        uuid        not null references units (id),
    landlord_id    uuid        not null references users (id),
    tenant_id      uuid        not null references users (id),
    invite_id      uuid unique references invites (id),
    rent_paise     bigint      not null check (rent_paise > 0),
    deposit_paise  bigint      not null default 0 check (deposit_paise >= 0),
    due_day        integer     not null check (due_day between 1 and 28),
    starts_on      date        not null,
    ends_on        date,
    status         varchar(16) not null default 'ACTIVE' check (status in ('ACTIVE', 'NOTICE', 'ENDED')),
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    version        bigint      not null default 0,
    check (ends_on is null or ends_on > starts_on)
);
create unique index leases_one_live_per_unit on leases (unit_id) where status in ('ACTIVE', 'NOTICE');
create index leases_landlord_idx on leases (landlord_id);
create index leases_tenant_idx on leases (tenant_id);
