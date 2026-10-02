-- The requests the system has already seen, by the Idempotency-Key they came with (ADR-086).
--
-- A row is a claim: "this owner's request with this key was done". It is inserted by the transaction
-- that does the work, as that transaction's first statement, so it commits with the work or is gone
-- with it. The primary key is the whole mechanism: two requests carrying one key cannot both insert
-- it, and the second waits for the first to commit or roll back.
--
-- The answer is added afterwards, once, by a transaction of its own. A row with no outcome is a claim
-- whose answer has not been stored yet, or never will be.
create table platform.idempotency_keys
(
    -- Who sent it: `anonymous`, or `subject:<id>`. Part of the identity, so one person's key tells
    -- another nothing.
    owner        text        not null,
    key          uuid        not null,
    -- SHA-256 over method, request target and body. The same key with another fingerprint is a client
    -- that reused a key.
    fingerprint  bytea       not null check (length(fingerprint) = 32),
    created_at   timestamptz not null,
    -- A claim past this instant is free to be taken over by the next request with its key.
    expires_at   timestamptz not null,
    -- Null: no answer yet. `replayed`: the answer below is given again. `withheld`: the answer carried
    -- a credential, so it was not kept and a repeat is told so.
    outcome      text check (outcome in ('replayed', 'withheld')),
    status       smallint,
    content_type text,
    body         bytea,
    primary key (owner, key),
    check (case outcome
               when 'replayed' then status is not null and status between 100 and 499 and body is not null
               else status is null and content_type is null and body is null
           end)
);

-- The sweep that deletes what has expired reads by this and nothing else.
create index idempotency_keys_expiry on platform.idempotency_keys (expires_at);
