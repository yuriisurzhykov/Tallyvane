-- Who people are (ADR-076, ADR-077): the account, and the external identity it is found by.
--
-- Only what registration writes and sign-in reads is here. The capabilities and the profile §8.3
-- describes arrive with the code that reads them, so a shape is decided by something that uses it.

create schema identity;

-- An account. The display name is checked by the code (one to 80 characters, no control characters);
-- the constraint below only refuses what no rule could allow. The email is the address Google reported
-- as verified: it is how the person is reached, never how they are found, so it is not unique (ADR-077:
-- an address can move between Google accounts).
create table identity.accounts
(
    id            uuid primary key,
    display_name  text        not null check (length(display_name) between 1 and 80),
    email         text        not null check (length(email) > 0),
    registered_at timestamptz not null,
    consented_at  timestamptz not null
);

-- How an account is found from outside: a provider's name for the person. Google's `sub` today; the
-- provider column is there so Apple (ADR-077) is a new value, not a new table. The primary key is what
-- makes two registrations of one person end with one account.
create table identity.external_identities
(
    provider   text        not null check (provider in ('google')),
    subject    text        not null check (length(subject) > 0),
    account_id uuid        not null references identity.accounts (id) on delete cascade,
    linked_at  timestamptz not null,
    primary key (provider, subject)
);

create index external_identities_account_id on identity.external_identities (account_id);
