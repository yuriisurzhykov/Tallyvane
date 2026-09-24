-- One persistent lockable slot prevents concurrent issuance bypassing cooldown.
-- Only keyed digests are stored; purpose and pending-operation binding are mandatory.
alter table identity.users add column email_verified boolean not null default true;

create table identity.email_challenges
(
    id uuid not null unique,
    email text collate platform.case_insensitive not null,
    purpose text not null check (purpose in ('REGISTRATION', 'EMAIL_LOGIN', 'PASSWORD_RESET', 'MFA')),
    binding text not null,
    hash text not null,
    expires_at timestamptz not null,
    resend_at timestamptz not null,
    remaining_attempts integer not null check (remaining_attempts >= 0),
    consumed boolean not null,
    primary key (email, purpose, binding)
);

create table identity.backup_codes
(
    user_id uuid not null references identity.users(id) on delete cascade,
    hash text not null,
    primary key (user_id, hash)
);
