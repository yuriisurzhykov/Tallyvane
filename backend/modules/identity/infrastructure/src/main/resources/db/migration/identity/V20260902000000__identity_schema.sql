-- Every table `identity`'s domain model needs its own row for: an account, its
-- credentials (one row per method, not one wide table — a fourth method adds a
-- table, not a column nobody else's row uses), a pending second-factor check, a
-- TOTP seed, a session, and the RFC 9700 §4.14.2 refresh-token ledger a session's
-- rotation history lives in.
--
-- `identity` is not Flyway's own bookkeeping schema (`platform` holds that, per
-- `platform_baseline.sql`), so this module creates the one schema it owns itself —
-- Flyway is told about `platform` only, per ADR-051.
create schema identity;

-- One row per human account. `email` uses `platform.case_insensitive` — the same
-- collation `platform_baseline.sql` created — so `Ivan@Mail.com` and
-- `ivan@mail.com` collide on the unique constraint instead of creating two
-- accounts; `domain/README.md` explains why the Kotlin `Email` type still compares
-- case-sensitively despite the column comparing without case.
create table identity.users
(
    id           uuid primary key,
    email        text collate platform.case_insensitive not null unique,
    display_name text,
    created_at   timestamptz                            not null,
    disabled_at  timestamptz
);

-- A password credential, kept in its own table rather than a nullable column on
-- `users`: an account with no password (Google-only) has no row here at all,
-- instead of a `NULL` a query has to remember to check. `password_hash` is
-- Argon2id's own self-describing encoded form — algorithm, cost parameters, salt
-- and hash together — so no separate salt or cost column exists.
create table identity.password_credentials
(
    user_id       uuid primary key references identity.users (id) on delete cascade,
    password_hash text not null
);

-- A Google credential, same reasoning as `password_credentials`: no row for an
-- account that never signed in with Google. `google_subject` is unique because it
-- is Google's own account identifier — two rows sharing one would mean two
-- Tallyvane accounts claim to be the same Google account, which
-- `GoogleSignInCompleter` refuses to create in the first place.
create table identity.google_credentials
(
    user_id        uuid primary key references identity.users (id) on delete cascade,
    google_subject text not null unique
);

-- A primary credential that checked out for an account with at least one second
-- factor enrolled — `AuthenticationCompleter`'s own row, deleted the moment the
-- second factor either succeeds or the entry expires, never updated in place.
-- `available_methods` is a Postgres array of `SecondFactorKind` names rather than
-- a join to a lookup table: the set is small, fixed by an enum in code, and never
-- queried by its members independently of the row that carries them.
create table identity.pending_authentications
(
    id                uuid primary key,
    user_id           uuid        not null references identity.users (id) on delete cascade,
    device            text        not null,
    available_methods text[]      not null,
    created_at        timestamptz not null,
    expires_at        timestamptz not null
);

-- One row per account with TOTP set up, `active` starting false until
-- `ConfirmSecondFactorEnrollmentUseCase` flips it — `domain/README.md`'s own
-- reasoning for why an unconfirmed enrollment must not protect anything yet.
-- `encrypted_secret` is the Tink ciphertext, base64-encoded text; the key that
-- decrypts it lives outside this database entirely, per the design's own
-- encryption-at-rest decision.
create table identity.totp_enrollments
(
    user_id          uuid primary key references identity.users (id) on delete cascade,
    encrypted_secret text        not null,
    active           boolean     not null,
    created_at       timestamptz not null
);

-- One signed-in device or browser. `current_access_token_hash` and its paired
-- `_pepper_version`/`_expires_at` are nullable for a reason narrower than "this
-- column is optional": `SessionIssuer.Default` inserts the session row and then
-- attaches its first access token as two separate calls inside the one
-- transaction the calling use case opened, so a row is briefly hashless mid-
-- transaction. That window is never visible to another connection — proven
-- against a real Postgres, not assumed: `backend/playground/transactions/README.md`'s
-- 2026-09-02 entry shows a write inside a still-open transaction is invisible to
-- everyone but that transaction until it commits. `revoked_at` is nullable and
-- stays on the row instead of deleting it, so `listFor` can still show "you
-- signed this device out" after the fact.
create table identity.sessions
(
    id                                 uuid primary key,
    user_id                            uuid        not null references identity.users (id) on delete cascade,
    device                             text        not null,
    token_family_id                    uuid        not null,
    created_at                         timestamptz not null,
    last_used_at                       timestamptz not null,
    revoked_at                         timestamptz,
    current_access_token_hash          text,
    current_access_token_pepper_version integer,
    current_access_token_expires_at    timestamptz
);

-- The lookup every protected request makes: which session, if any, holds this
-- access token right now. Partial rather than plain unique, since the column is
-- null for the brief window `sessions`' own comment describes, and a null there
-- carries no session to find anyway.
create unique index sessions_current_access_token_hash_idx
    on identity.sessions (current_access_token_hash)
    where current_access_token_hash is not null;

-- `revokeAllFor(userId)` and `listFor(userId)` both filter on this; without the
-- index each becomes a sequential scan of every session ever issued, not just
-- one account's.
create index sessions_user_id_idx on identity.sessions (user_id);

-- The RFC 9700 §4.14.2 rotation-with-reuse-detection ledger: every refresh token
-- this module has ever minted, active or not, so a token presented a second time
-- after it was already rotated away is detectable as reuse rather than merely
-- "unknown". `hash` is the primary key rather than a separate surrogate `id`:
-- nothing ever looks a row up by an identity of its own, every lookup is by
-- `hash`, `session_id` or `family_id`, and a row is immutable once inserted
-- (rotation inserts a new row rather than reusing one), so `hash` already has
-- everything a primary key needs — uniqueness and no reason to change. `status`
-- is `active`/`consumed`/`revoked` rather than a second `sessions.revoked_at`-
-- style timestamp pair, because `RefreshTokenStore.rotate` needs to flip exactly
-- one row atomically and a `check` constraint on a single column is what makes
-- "was this still active the instant I tried to consume it" a single conditional
-- `update`, not a read-then-write racing a concurrent one.
create table identity.refresh_tokens
(
    hash           text primary key,
    family_id      uuid        not null,
    session_id     uuid        not null references identity.sessions (id) on delete cascade,
    pepper_version integer     not null,
    status         text        not null check (status in ('active', 'consumed', 'revoked')),
    issued_at      timestamptz not null,
    expires_at     timestamptz not null,
    consumed_at    timestamptz
);

-- `revokeAllFor(sessionId)` filters on this to revoke every still-active token in
-- one session's lineage — reuse detection's own remediation step.
create index refresh_tokens_session_id_idx on identity.refresh_tokens (session_id);
