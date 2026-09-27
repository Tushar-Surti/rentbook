-- A caretaker (a PG's warden, a building's manager) works for one landlord on the properties the landlord
-- assigns: they see those registers, record rent paid in cash and look after repair requests, and nothing
-- else. They join from an emailed invite, like tenants, and the landlord can remove them at any time.
do $$
declare
    existing text;
begin
    select conname into existing from pg_constraint
    where conrelid = 'users'::regclass and contype = 'c' and pg_get_constraintdef(oid) like '%TENANT%';
    execute format('alter table users drop constraint %I', existing);
end $$;

alter table users add constraint users_role_check check (role in ('LANDLORD', 'TENANT', 'CARETAKER'));

create table caretakers (
    id                 uuid primary key,
    landlord_id        uuid          not null references users (id),
    user_id            uuid          unique references users (id),
    full_name          varchar(120)  not null,
    email              varchar(254)  not null,
    phone              varchar(20),
    status             varchar(16)   not null check (status in ('INVITED', 'ACTIVE', 'REMOVED')),
    token_hash         varchar(64)   unique,
    invite_expires_at  timestamptz,
    created_at         timestamptz   not null,
    updated_at         timestamptz   not null,
    version            bigint        not null default 0
);

create unique index caretakers_one_per_email on caretakers (landlord_id, lower(email)) where status <> 'REMOVED';

create table caretaker_properties (
    caretaker_id  uuid  not null references caretakers (id) on delete cascade,
    property_id   uuid  not null references properties (id),
    primary key (caretaker_id, property_id)
);

create index caretaker_properties_property on caretaker_properties (property_id);
