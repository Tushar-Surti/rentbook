-- A vacant flat, room or bed listed publicly: a shareable page with the rent, photos and an enquiry form.
-- A listing closes when someone moves in, or when the landlord closes it.
create table listings (
    id              uuid primary key,
    landlord_id     uuid           not null references users (id),
    unit_id         uuid           not null references units (id),
    slug            varchar(16)    not null unique,
    status          varchar(16)    not null check (status in ('OPEN', 'CLOSED')),
    rent_paise      bigint         not null check (rent_paise > 0),
    deposit_paise   bigint         not null default 0 check (deposit_paise >= 0),
    available_from  date           not null,
    description     varchar(2000),
    closed_at       timestamptz,
    created_at      timestamptz    not null,
    updated_at      timestamptz    not null,
    version         bigint         not null default 0
);

create unique index listings_one_open_per_unit on listings (unit_id) where status = 'OPEN';
create index listings_landlord on listings (landlord_id, created_at desc);

create table listing_photos (
    id            uuid primary key,
    listing_id    uuid          not null references listings (id) on delete cascade,
    storage_key   varchar(300)  not null unique,
    content_type  varchar(100)  not null,
    size_bytes    bigint,
    status        varchar(16)   not null check (status in ('PENDING', 'AVAILABLE')),
    created_at    timestamptz   not null
);

create index listing_photos_listing on listing_photos (listing_id, created_at);

create table listing_enquiries (
    id          uuid primary key,
    listing_id  uuid           not null references listings (id) on delete cascade,
    name        varchar(120)   not null,
    phone       varchar(20)    not null,
    email       varchar(254),
    message     varchar(1000),
    visit_on    date,
    status      varchar(16)    not null check (status in ('NEW', 'INVITED', 'DISMISSED')),
    created_at  timestamptz    not null
);

create index listing_enquiries_listing on listing_enquiries (listing_id, created_at desc);
