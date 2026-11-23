-- The person's words and the facts that quote them (docs/backend/08-product-core.md, ADR-102).
--
-- `account_id` names an account of `identity` and is not a foreign key: a schema is one module's own (ADR-059).
-- Words are only ever added. A story's facts are rewritten whole when it is saved; a corrected fact stays,
-- with `replaced_by` naming the fact that replaced it.

create schema stories;

create table stories.utterances
(
    id         uuid primary key,
    account_id uuid        not null,
    story_id   uuid        not null,
    words      text        not null check (length(words) between 1 and 20000),
    said_at    timestamptz not null
);

create index utterances_account on stories.utterances (account_id);

create table stories.stories
(
    id         uuid primary key,
    account_id uuid    not null,
    title      text,
    company    text,
    position   text,
    started    text,
    ended      text,
    finished   boolean not null,
    -- The order stories are shown in: a later one has a larger number.
    shelf      bigint generated always as identity
);

create index stories_account on stories.stories (account_id, shelf);

create table stories.facts
(
    id           uuid primary key,
    story_id     uuid    not null references stories.stories (id) on delete cascade,
    place        integer not null,
    kind         text    not null,
    text         text    not null,
    figures      text    not null,
    technologies text    not null,
    skills       text    not null,
    mine         boolean not null,
    replaced_by  uuid
);

create index facts_story on stories.facts (story_id, place);

create table stories.quotes
(
    fact_id      uuid    not null references stories.facts (id) on delete cascade,
    place        integer not null,
    utterance_id uuid    not null references stories.utterances (id),
    fragment     text    not null,
    primary key (fact_id, place)
);
