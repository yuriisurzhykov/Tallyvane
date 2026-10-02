-- What a person was given once they proved who they are (ADR-079): the session, found by the keyed
-- digest of the secret their browser holds.
--
-- Only what a request reads and sign-in writes is here. The device description and the list of a
-- person's sessions arrive with the settings screen (slice 4), so a shape is decided by something that
-- uses it. `account_id` names an account of `identity` and is not a foreign key: a schema is one
-- module's own (ADR-059), and what to do with the sessions of a deleted account is that deletion's to
-- say.

create schema sessions;

create table sessions.sessions
(
    id               uuid primary key,
    secret_digest    bytea       not null check (length(secret_digest) > 0),
    pepper_version   integer     not null check (pepper_version >= 1),
    account_id       uuid        not null,
    authenticated_at timestamptz not null,
    last_active_at   timestamptz not null check (last_active_at >= authenticated_at)
);

-- One lookup by this is all a request costs. Unique across pepper versions too: a digest that is the
-- same bytes under two versions is a collision, not two sessions.
create unique index sessions_secret_digest on sessions.sessions (secret_digest);

-- Sign-out everywhere and the settings list read a person's sessions by account.
create index sessions_account_id on sessions.sessions (account_id);

-- How the person proved who they are, as RFC 8176 `amr` names it. A session is replaced, never
-- edited, so these are written once with it and go with it.
create table sessions.session_factors
(
    session_id uuid not null references sessions.sessions (id) on delete cascade,
    factor     text not null check (factor in ('google', 'totp', 'recovery_code')),
    primary key (session_id, factor)
);
