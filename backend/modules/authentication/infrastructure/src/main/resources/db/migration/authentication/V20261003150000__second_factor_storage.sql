-- What a second factor needs kept (slice 5b, ADR-093): a TOTP enrolment, the recovery codes that go with
-- it, and the wrong codes typed for an account.
--
-- No column here names a row of another module's schema. `account_id` is the account's id as the identity
-- module tells it, and what happens to these rows when an account goes is decided where accounts are
-- deleted, not by a foreign key across schemas.

-- At most one enrolment per account. The seed is sealed (AES-256-GCM, a keyset the deployment holds), so a
-- copy of the table is not a copy of anyone's authenticator. `last_accepted_step` is the time step of the
-- last code taken: a code is single use, and a second request that read the same row waits for the first
-- under `for update` and then finds the step spent.
create table authentication.totp_enrollments
(
    account_id         uuid primary key,
    sealed_seed        text   not null check (length(sealed_seed) > 0),
    standing           text   not null check (standing in ('pending', 'active', 'retired')),
    last_accepted_step bigint,
    constraint pending_has_accepted_nothing check ((standing = 'pending') = (last_accepted_step is null))
);

-- The whole set is replaced at once, so a code is only ever a digest (HMAC with the pepper, as other
-- secrets here) and a time it was spent. Position keeps the order the codes were issued in.
create table authentication.recovery_codes
(
    account_id     uuid    not null references authentication.totp_enrollments (account_id) on delete cascade,
    position       integer not null check (position >= 1),
    digest         bytea   not null,
    pepper_version integer not null check (pepper_version >= 1),
    spent_at       timestamptz,
    primary key (account_id, position),
    unique (account_id, digest, pepper_version)
);

-- The wrong TOTP codes typed for an account, whichever attempt they came in. Rows older than the window
-- the limit looks at are removed as new ones are written, so the table stays as small as the guessing.
create table authentication.second_factor_failures
(
    id         bigint generated always as identity primary key,
    account_id uuid        not null,
    failed_at  timestamptz not null
);

create index second_factor_failures_by_account on authentication.second_factor_failures (account_id, failed_at);
