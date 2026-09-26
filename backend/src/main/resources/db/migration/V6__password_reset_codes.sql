-- Emailed codes now serve two purposes: proving a new landlord's inbox, and resetting a forgotten
-- password. An address can hold one code of each kind.
alter table email_codes
    add column purpose varchar(16) not null default 'SIGNUP' check (purpose in ('SIGNUP', 'RESET'));
alter table email_codes drop constraint email_codes_pkey;
alter table email_codes add primary key (email, purpose);
