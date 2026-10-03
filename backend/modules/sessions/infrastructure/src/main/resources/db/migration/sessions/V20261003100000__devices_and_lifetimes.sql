-- What the list of devices shows, and the lifetimes sessions live by (ADR-090).
--
-- A session gets the kind of client that holds it and the device it is on, kept as parts so that the
-- screen builds its own wording. The rows that exist were all made by a browser whose `User-Agent`
-- nobody kept, so they are `other` on `other`; they are replaced within a week at the longest.

alter table sessions.sessions
    add column client_type     text    not null default 'browser' check (client_type in ('browser')),
    add column device_browser  text    not null default 'other'
        check (device_browser in ('chrome', 'edge', 'firefox', 'opera', 'safari', 'other')),
    add column device_platform text    not null default 'other'
        check (device_platform in ('windows', 'macos', 'linux', 'android', 'ios', 'chromeos', 'other')),
    add column device_mobile   boolean not null default false,
    -- What the person called the device. Null until they do.
    add column device_name     text check (device_name is null or char_length(device_name) between 1 and 60);

-- The defaults were for the rows that already exist. A session written from now on says what it is.
alter table sessions.sessions
    alter column client_type drop default,
    alter column device_browser drop default,
    alter column device_platform drop default,
    alter column device_mobile drop default;

-- One version of the lifetimes for one kind of client. Numbered from 1 within the kind; never edited.
--
-- The bounds of the numbers (an idle limit of 15 minutes to 30 days, an absolute limit of at most 90
-- days) are not checks here. The code sets them (ADR-078) and a bound repeated in a constraint would
-- need a migration to change what a release is supposed to change on its own. The checks below only
-- refuse what no bound could allow.
create table sessions.lifetime_versions
(
    client_type     text        not null check (client_type in ('browser')),
    number          integer     not null check (number >= 1),
    idle_millis     bigint      not null check (idle_millis > 0),
    absolute_millis bigint      not null check (absolute_millis >= idle_millis),
    created_at      timestamptz not null,
    primary key (client_type, number)
);

-- Which version was in force, and from when. Activating, rolling back and activating again are all
-- rows added to the end; the version in force for a kind of client is the one named by its last row.
-- The identity column orders them, because two activations may carry the same instant.
create table sessions.lifetime_activations
(
    id           bigint generated always as identity primary key,
    client_type  text        not null,
    number       integer     not null,
    activated_at timestamptz not null,
    foreign key (client_type, number) references sessions.lifetime_versions (client_type, number)
);

create index lifetime_activations_latest on sessions.lifetime_activations (client_type, id desc);

-- The history of how long sessions were allowed to live is evidence. A version, and each activation, is
-- refused an UPDATE or DELETE, so no code path and no hand-typed statement can rewrite it.
create function sessions.refuse_change() returns trigger
    language plpgsql as
$$
begin
    raise exception '% on sessions.% is refused: lifetime history is only ever added to. Add a new version or activation instead.',
        tg_op, tg_table_name;
end
$$;

create trigger lifetime_versions_are_never_changed
    before update or delete
    on sessions.lifetime_versions
    for each row
execute function sessions.refuse_change();

create trigger lifetime_activations_are_never_changed
    before update or delete
    on sessions.lifetime_activations
    for each row
execute function sessions.refuse_change();

-- What a browser starts with: a day unused, a week at most (ADR-079), as version 1, in force.
--
-- Written as data rather than created by code at startup, so there is no moment after a deploy when a
-- request has no lifetimes to be judged by.
insert into sessions.lifetime_versions (client_type, number, idle_millis, absolute_millis, created_at)
values ('browser', 1, 86400000, 604800000, now());

insert into sessions.lifetime_activations (client_type, number, activated_at)
values ('browser', 1, now());
