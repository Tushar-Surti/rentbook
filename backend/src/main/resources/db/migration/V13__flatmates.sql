-- Flatmates: friends sharing one flat (or a room let whole), each on a lease of their own for their share of
-- the rent, with their own deposit, ledger and receipts. A unit may now hold several live leases, never two
-- for the same person; beds stay one person each, which the application enforces.
drop index leases_one_live_per_unit;
create unique index leases_one_live_per_tenant_unit on leases (unit_id, tenant_id) where status in ('ACTIVE', 'NOTICE');
create index leases_unit_idx on leases (unit_id);

-- Flatmates may be invited together, so several invites to one unit may be pending at once.
drop index invites_one_pending_per_unit;
create index invites_unit_pending on invites (unit_id) where status = 'PENDING';

alter table invites add column flatmate boolean not null default false;
