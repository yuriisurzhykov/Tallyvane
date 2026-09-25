create table identity.authentication_policy
(
    id                    smallint primary key check (id = 1),
    version               bigint not null check (version > 0),
    advanced_acknowledged boolean not null
);

create table identity.authentication_policy_rules
(
    primary_method text primary key check (primary_method in ('PASSWORD', 'GOOGLE', 'EMAIL_CODE')),
    enabled        boolean not null,
    requirement    text not null check (requirement in ('DISABLED', 'IF_ENROLLED', 'REQUIRED')),
    allowed_methods text[] not null
);

insert into identity.authentication_policy (id, version, advanced_acknowledged) values (1, 1, false);
insert into identity.authentication_policy_rules (primary_method, enabled, requirement, allowed_methods) values
    ('PASSWORD', true, 'IF_ENROLLED', array['TOTP', 'EMAIL_OTP', 'BACKUP_CODE']),
    ('GOOGLE', true, 'IF_ENROLLED', array['TOTP', 'BACKUP_CODE']),
    ('EMAIL_CODE', true, 'IF_ENROLLED', array['TOTP', 'BACKUP_CODE']);

create table identity.authentication_policy_audit
(
    id bigint generated always as identity primary key,
    actor_user_id uuid not null references identity.users(id) on delete cascade,
    action text not null check (action in (
        'POLICY_READ_DENIED', 'POLICY_UPDATE_DENIED', 'POLICY_UPDATE_CONFLICT',
        'POLICY_UPDATE_INVALID', 'POLICY_UPDATED'
    )),
    policy_version bigint not null check (policy_version >= 0),
    occurred_at timestamptz not null
);
