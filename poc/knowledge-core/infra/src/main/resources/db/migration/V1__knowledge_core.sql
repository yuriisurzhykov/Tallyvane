-- The schema of ADR-101. `${embed_dim}` is the dimension of the embedding model (EMBED_DIM), passed in by
-- Flyway as a placeholder: a different model needs a fresh database.
create extension if not exists vector;

create table raw_entry (
    id          uuid primary key,
    source      text        not null,
    question_id uuid,
    context     jsonb       not null,
    text        text        not null,
    created_at  timestamptz not null default now()
);

create table company (
    id        uuid primary key,
    name      text not null,
    name_norm text not null unique
);

create table role (
    id         uuid primary key,
    seq        bigint generated always as identity,
    company_id uuid not null references company (id) on delete cascade,
    title      text,
    start_date text,
    end_date   text,
    is_current boolean
);

create table project (
    id                uuid primary key,
    seq               bigint generated always as identity,
    role_id           uuid    not null references role (id) on delete cascade,
    title             text,
    problem           text,
    canonical_bullets jsonb,
    closed            boolean not null default false
);

create table action (
    id         uuid primary key,
    seq        bigint generated always as identity,
    project_id uuid not null references project (id) on delete cascade,
    kind       text not null check (kind in ('investigated', 'designed', 'decided', 'built', 'communicated', 'led', 'mentored', 'improved', 'other')),
    verb       text not null,
    what       text not null,
    finding    text,
    embedding  vector(${embed_dim}) not null,
    created_at timestamptz not null default now()
);

create table outcome (
    id         uuid primary key,
    seq        bigint generated always as identity,
    project_id uuid    not null references project (id) on delete cascade,
    what       text    not null,
    dir        text check (dir in ('up', 'down')),
    value      numeric,
    unit       text,
    approx     boolean not null,
    has_metric boolean not null
);

create table action_outcome (
    action_id  uuid not null references action (id) on delete cascade,
    outcome_id uuid not null references outcome (id) on delete cascade,
    primary key (action_id, outcome_id)
);

-- `seeded` marks the technologies that came with the product: a reset keeps those and drops the rest.
create table technology (
    id      uuid primary key,
    name    text    not null unique,
    aliases text[]  not null default '{}',
    seeded  boolean not null default false
);

create table action_technology (
    action_id     uuid not null references action (id) on delete cascade,
    technology_id uuid not null references technology (id) on delete cascade,
    primary key (action_id, technology_id)
);

create table collaborator (
    id        uuid primary key,
    role_name text not null unique
);

create table action_collaborator (
    action_id       uuid not null references action (id) on delete cascade,
    collaborator_id uuid not null references collaborator (id) on delete cascade,
    primary key (action_id, collaborator_id)
);

-- A skill with no embedding yet is embedded when the application starts.
create table skill (
    id        uuid primary key,
    slug      text   not null unique,
    name      text   not null,
    aliases   text[] not null default '{}',
    embedding vector(${embed_dim})
);

create table action_skill (
    action_id uuid not null references action (id) on delete cascade,
    skill_id  uuid not null references skill (id) on delete cascade,
    weight    real not null check (weight between 0 and 1),
    primary key (action_id, skill_id)
);

-- Points at an entity by type and id and not by a foreign key, since the entity is in one of several tables.
create table evidence (
    id           uuid primary key,
    seq          bigint generated always as identity,
    entity_type  text not null,
    entity_id    uuid not null,
    raw_entry_id uuid not null references raw_entry (id),
    quote        text not null
);

create index evidence_entity on evidence (entity_id);

create table question_log (
    id          uuid primary key,
    seq         bigint generated always as identity,
    target_type text,
    target_id   uuid,
    gap_code    text    not null,
    text        text    not null,
    answered    boolean not null default false
);

create table vacancy (
    id        uuid primary key,
    title     text  not null,
    reqs      jsonb not null,
    unmapped  jsonb not null
);

-- The position of the interview; one row, kept across restarts.
create table cursor_state (
    id         int primary key check (id = 1),
    company_id uuid,
    role_id    uuid,
    project_id uuid,
    stage      text not null
);
