-- One-time codes that prove a new landlord can read the inbox they sign up with. One row per address:
-- asking again replaces the code, and creating the account deletes it.
create table email_codes (
    email       varchar(254)  primary key,
    code_hash   varchar(64)   not null,
    attempts    int           not null default 0,
    sent_at     timestamptz   not null,
    expires_at  timestamptz   not null
);
