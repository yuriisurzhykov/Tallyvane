-- What the model already answered, and what asking it cost (docs/specs/model).
--
-- `model_memo` is shared by every account: its key is the SHA-256 of the spec, the model, the instructions and
-- the input, so the same question is paid for once. Rows are only ever added.
create table platform.model_memo
(
    key           text primary key check (key ~ '^[0-9a-f]{64}$'),
    spec          text        not null,
    model         text        not null,
    reply         text        not null,
    tokens_in     bigint      not null check (tokens_in >= 0),
    tokens_out    bigint      not null check (tokens_out >= 0),
    tokens_cached bigint      not null check (tokens_cached >= 0),
    created_at    timestamptz not null default now()
);

-- One row for an account, a day and a spec: the daily budget is read from it, and so is "what did this cost".
-- `account_id` names an account of `identity` and is not a foreign key (ADR-059).
create table platform.model_spending
(
    account_id    uuid   not null,
    day           text   not null check (day ~ '^\d{4}-\d{2}-\d{2}$'),
    spec          text   not null,
    calls         integer not null check (calls > 0),
    tokens_in     bigint not null check (tokens_in >= 0),
    tokens_out    bigint not null check (tokens_out >= 0),
    tokens_cached bigint not null check (tokens_cached >= 0),
    primary key (account_id, day, spec)
);

-- The model providers an administrator set up; exactly one may be active. The key is sealed with Tink and only
-- its last characters are kept in the clear, for the screen.
create table platform.model_providers
(
    id            uuid primary key,
    name          text             not null unique check (length(name) between 1 and 60),
    kind          text             not null check (kind in ('Anthropic', 'OpenAiCompatible')),
    base_url      text check (base_url is null or base_url ~ '^https?://'),
    sealed_key    text             not null,
    key_hint      text             not null,
    fast_model    text             not null check (length(fast_model) between 1 and 120),
    careful_model text             not null check (length(careful_model) between 1 and 120),
    price_in      double precision not null check (price_in >= 0),
    price_out     double precision not null check (price_out >= 0),
    active        boolean          not null,
    updated_at    timestamptz      not null
);

create unique index model_providers_one_active on platform.model_providers (active) where active;

-- Every model call and what it cost, to compare providers, models and specs. Only ever added.
create table platform.model_calls
(
    id            bigint generated always as identity primary key,
    at            timestamptz      not null,
    account_id    uuid             not null,
    spec          text             not null,
    provider_id   uuid             not null,
    model         text             not null,
    tokens_in     bigint           not null check (tokens_in >= 0),
    tokens_out    bigint           not null check (tokens_out >= 0),
    tokens_cached bigint           not null check (tokens_cached >= 0),
    cost_usd      double precision not null check (cost_usd >= 0),
    remembered    boolean          not null
);

create index model_calls_at on platform.model_calls (at);
