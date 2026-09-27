-- Administrator identities have their own account, credential, MFA, challenge, and session rows.
-- `admins` intentionally has no foreign key to `users`: the two records have independent IDs and
-- may share an email address after the one-time bootstrap copies credentials for allowlisted users.
create table identity.admins
(
    id            uuid primary key,
    email         text collate platform.case_insensitive not null unique,
    display_name  text,
    created_at    timestamptz                            not null,
    disabled_at   timestamptz,
    email_verified boolean                               not null
);

create table identity.admin_password_credentials
(
    admin_id      uuid primary key references identity.admins (id) on delete cascade,
    password_hash text not null
);

create table identity.admin_google_credentials
(
    admin_id       uuid primary key references identity.admins (id) on delete cascade,
    google_subject text not null unique
);

create table identity.admin_pending_authentications
(
    id                uuid primary key,
    admin_id          uuid        not null references identity.admins (id) on delete cascade,
    device            text        not null,
    recommended_method text       not null check (recommended_method in ('TOTP', 'EMAIL_OTP')),
    available_methods text[]      not null,
    created_at        timestamptz not null,
    expires_at        timestamptz not null,
    policy_version    bigint      not null check (policy_version > 0)
);

create table identity.admin_totp_enrollments
(
    admin_id         uuid primary key references identity.admins (id) on delete cascade,
    encrypted_secret text        not null,
    active           boolean     not null,
    created_at       timestamptz not null
);

create table identity.admin_email_mfa_enrollments
(
    admin_id uuid primary key references identity.admins (id) on delete cascade
);

create table identity.admin_sessions
(
    id                                   uuid primary key,
    admin_id                             uuid        not null references identity.admins (id) on delete cascade,
    device                               text        not null,
    token_family_id                      uuid        not null,
    created_at                           timestamptz not null,
    last_used_at                         timestamptz not null,
    revoked_at                           timestamptz,
    reauthenticated_at                   timestamptz,
    current_access_token_hash            text,
    current_access_token_pepper_version  integer,
    current_access_token_expires_at      timestamptz
);

create unique index admin_sessions_current_access_token_hash_idx
    on identity.admin_sessions (current_access_token_hash)
    where current_access_token_hash is not null;

create index admin_sessions_admin_id_idx on identity.admin_sessions (admin_id);

create table identity.admin_refresh_tokens
(
    hash           text primary key,
    family_id      uuid        not null,
    session_id     uuid        not null references identity.admin_sessions (id) on delete cascade,
    pepper_version integer     not null,
    status         text        not null check (status in ('active', 'consumed', 'revoked')),
    issued_at      timestamptz not null,
    expires_at     timestamptz not null,
    consumed_at    timestamptz
);

create index admin_refresh_tokens_session_id_idx on identity.admin_refresh_tokens (session_id);

create table identity.admin_email_challenges
(
    id                 uuid        not null unique,
    email              text collate platform.case_insensitive not null,
    purpose            text        not null check (purpose in ('REGISTRATION', 'EMAIL_LOGIN', 'PASSWORD_RESET', 'MFA')),
    binding            text        not null,
    hash               text        not null,
    expires_at         timestamptz not null,
    resend_at          timestamptz not null,
    remaining_attempts integer     not null check (remaining_attempts >= 0),
    consumed           boolean     not null,
    primary key (email, purpose, binding)
);

create table identity.admin_backup_codes
(
    admin_id uuid not null references identity.admins (id) on delete cascade,
    hash     text not null,
    primary key (admin_id, hash)
);

create table identity.admin_authentication_action_proofs
(
    hash           text primary key,
    pepper_version integer not null,
    admin_id       uuid not null references identity.admins (id) on delete cascade,
    session_id     uuid not null references identity.admin_sessions (id) on delete cascade,
    action         text not null check (action in ('CHANGE_PRIMARY_CREDENTIAL', 'MANAGE_SECOND_FACTORS')),
    policy_version bigint not null check (policy_version > 0),
    scheme_id      text not null,
    assurance_rank integer not null check (assurance_rank > 0),
    expires_at     timestamptz not null,
    consumed_at    timestamptz
);

create index admin_authentication_action_proofs_expiry_idx
    on identity.admin_authentication_action_proofs (expires_at);

create table identity.admin_authentication_policy_audit
(
    id             bigint generated always as identity primary key,
    admin_id       uuid        not null references identity.admins (id) on delete cascade,
    action         text        not null check (
        action in (
            'POLICY_READ_DENIED', 'POLICY_UPDATE_DENIED', 'POLICY_UPDATE_CONFLICT',
            'POLICY_UPDATE_INVALID', 'POLICY_UPDATED'
        )
        or action ~ '^MFA_RESET:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
    ),
    policy_version bigint      not null check (policy_version >= 0),
    occurred_at    timestamptz not null
);
