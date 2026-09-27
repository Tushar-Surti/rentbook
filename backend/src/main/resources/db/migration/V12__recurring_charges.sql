-- Charges that come round every month with the rent: Wi-Fi, meals, parking, laundry, maintenance.
-- Each month's line is an ordinary charge, marked with the add-on and month it came from so it's made once.
create table recurring_charges (
    id            uuid primary key,
    lease_id      uuid          not null references leases (id),
    kind          varchar(16)   not null check (kind in ('UTILITY', 'OTHER')),
    label         varchar(80)   not null,
    amount_paise  bigint        not null check (amount_paise > 0),
    starts_month  date          not null check (extract(day from starts_month) = 1),
    ends_month    date          check (ends_month is null or extract(day from ends_month) = 1),
    created_by    uuid          references users (id),
    created_at    timestamptz   not null,
    updated_at    timestamptz   not null,
    version       bigint        not null default 0
);

create index recurring_charges_lease on recurring_charges (lease_id);

alter table charges
    add column recurring_id uuid references recurring_charges (id),
    add column recurring_month date,
    add constraint charges_recurring_month check ((recurring_id is null) = (recurring_month is null));

create unique index charges_one_addon_per_month on charges (recurring_id, recurring_month) where recurring_id is not null;
