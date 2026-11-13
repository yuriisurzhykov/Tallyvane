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
