-- What the authentication module keeps: sign-in attempts, and the versions of the policy each
-- purpose is judged by, with a record of which version was in force when (ADR-078, ADR-085).
--
-- Tables for accounts, factors, sessions and the security journal are not here. Each arrives with the
-- code that first uses it, so a shape is decided by something that reads it rather than guessed.

create schema authentication;

-- A sign-in attempt, kept apart from the lists it grows. The attempt only ever gains rows: a
-- verified factor or a wrong answer, never a change to one. Position numbers them in the order the
-- domain told them, and the primary key on (attempt_id, position) is what makes two requests that
-- both record "the next wrong answer" collide instead of one silently replacing the other.
create table authentication.attempts
(
    id         uuid primary key,
    purpose    text        not null check (purpose in ('registration', 'login', 'admin_login', 'step_up')),
    started_at timestamptz not null
);

create table authentication.attempt_verified_factors
(
    attempt_id  uuid        not null references authentication.attempts (id) on delete cascade,
    position    integer     not null check (position >= 1),
    kind        text        not null check (kind in ('google', 'totp', 'recovery_code')),
    verified_at timestamptz not null,
    primary key (attempt_id, position)
);

create table authentication.attempt_failures
(
    attempt_id uuid        not null references authentication.attempts (id) on delete cascade,
    position   integer     not null check (position >= 1),
    failed_at  timestamptz not null,
    primary key (attempt_id, position)
);

-- One version of the policy for one purpose. Numbered from 1 within the purpose; never edited.
--
-- The bounds of the numbers (an attempt lives 1 to 15 minutes, and so on) are not checks here. The code
-- sets them (ADR-078), and a bound repeated in a constraint would need a migration to change what a
-- release is supposed to change on its own. The checks below only refuse what no bound could allow.
create table authentication.policy_versions
(
    purpose                 text        not null check (purpose in ('registration', 'login', 'admin_login', 'step_up')),
    number                  integer     not null check (number >= 1),
    attempt_lifetime_millis bigint      not null check (attempt_lifetime_millis > 0),
    max_failures            integer     not null check (max_failures > 0),
    first_delay_millis      bigint      not null check (first_delay_millis > 0),
    created_at              timestamptz not null,
    -- The transaction that made the version, which is the only one allowed to give it steps (see
    -- refuse_late_step below). It is filled in by the database and never by the code.
    made_in_transaction     bigint      not null default txid_current(),
    primary key (purpose, number)
);

-- The steps of a version, in the order they apply.
create table authentication.policy_version_steps
(
    purpose   text    not null,
    number    integer not null,
    position  integer not null check (position >= 1),
    necessity text    not null check (necessity in ('always', 'when_enrolled')),
    primary key (purpose, number, position),
    foreign key (purpose, number) references authentication.policy_versions (purpose, number)
);

-- The kinds of factor that satisfy a step: any one of them will do.
create table authentication.policy_version_step_kinds
(
    purpose  text    not null,
    number   integer not null,
    position integer not null,
    kind     text    not null check (kind in ('google', 'totp', 'recovery_code')),
    primary key (purpose, number, position, kind),
    foreign key (purpose, number, position) references authentication.policy_version_steps (purpose, number, position)
);

-- Which version was in force, and from when. Activating, rolling back and activating again are all
-- rows added to the end; the version in force for a purpose is the one named by its last row. The
-- identity column orders them, because two activations may carry the same instant.
create table authentication.policy_activations
(
    id           bigint generated always as identity primary key,
    purpose      text        not null,
    number       integer     not null,
    activated_at timestamptz not null,
    foreign key (purpose, number) references authentication.policy_versions (purpose, number)
);

create index policy_activations_latest on authentication.policy_activations (purpose, id desc);

-- The history of what the system demanded is evidence. A version, and each activation, is refused
-- an UPDATE or DELETE here, so no code path and no hand-typed statement can rewrite it.
create function authentication.refuse_change() returns trigger
    language plpgsql as
$$
begin
    raise exception '% on authentication.% is refused: policy history is only ever added to. Add a new version or activation instead.',
        tg_op, tg_table_name;
end
$$;

-- The steps of a version, and the kinds that satisfy each, are part of it. They are written together
-- with the version, in the one transaction that makes it. A row added to a version that was already
-- committed would change what that version demands, with no new version and no activation to show
-- for it, and the history would no longer say what was asked of whom.
create function authentication.refuse_late_step() returns trigger
    language plpgsql as
$$
begin
    if not exists (select 1
                   from authentication.policy_versions v
                   where v.purpose = new.purpose
                     and v.number = new.number
                     and v.made_in_transaction = txid_current()) then
        raise exception 'INSERT on authentication.% is refused: the steps of version % of % are written together with the version. Add a new version instead.',
            tg_table_name, new.number, new.purpose;
    end if;
    return new;
end
$$;

create trigger policy_versions_are_never_changed
    before update or delete
    on authentication.policy_versions
    for each row
execute function authentication.refuse_change();

create trigger policy_version_steps_are_never_changed
    before update or delete
    on authentication.policy_version_steps
    for each row
execute function authentication.refuse_change();

create trigger policy_version_step_kinds_are_never_changed
    before update or delete
    on authentication.policy_version_step_kinds
    for each row
execute function authentication.refuse_change();

create trigger policy_activations_are_never_changed
    before update or delete
    on authentication.policy_activations
    for each row
execute function authentication.refuse_change();

create trigger policy_version_steps_are_written_with_their_version
    before insert
    on authentication.policy_version_steps
    for each row
execute function authentication.refuse_late_step();

create trigger policy_version_step_kinds_are_written_with_their_version
    before insert
    on authentication.policy_version_step_kinds
    for each row
execute function authentication.refuse_late_step();
