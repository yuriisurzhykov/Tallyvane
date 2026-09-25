create table identity.authentication_action_proofs
(
    hash           text primary key,
    pepper_version integer not null,
    user_id        uuid not null references identity.users (id) on delete cascade,
    session_id     uuid not null references identity.sessions (id) on delete cascade,
    action         text not null check (action in ('CHANGE_PRIMARY_CREDENTIAL', 'MANAGE_SECOND_FACTORS')),
    policy_version bigint not null check (policy_version > 0),
    scheme_id      text not null,
    assurance_rank integer not null check (assurance_rank > 0),
    expires_at     timestamptz not null,
    consumed_at    timestamptz
);

create index authentication_action_proofs_expiry_idx
    on identity.authentication_action_proofs (expires_at);
