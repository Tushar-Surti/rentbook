-- ends_on is the tenant's last day, so it may be the day they moved in (a move-in that fell through).
alter table leases drop constraint leases_check;
alter table leases add constraint leases_ends_on_check check (ends_on is null or ends_on >= starts_on);
