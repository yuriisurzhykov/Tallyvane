# ADR-085. A stored aggregate tells its state to a record, and is restored from a replay of the same words

## Status

Accepted. Chosen by the owner on 2026-10-01 as option A of the question "how does closed state leave
the domain" (`auth-design/12-boundary-question.md`), after the domain had been written with every field
private and no getter, and before the first adapter was.

## Context

The authentication domain (ADR-078) hides its state on purpose. `Attempt` keeps its factors and wrong
answers private; `Progress` and `DraftCheck` are read only through a `Report` they dispatch to. The
reason is not style: an `Attempt` that handed out its failure list could be handed back with the list
emptied, and the failure limit would be a number the caller chooses.

Two places need that state anyway. A sign-in attempt is spread over several requests, so it has to be
kept somewhere that survives a restart and is not the client; a policy version has to be kept for
years. Storage lives in `infrastructure`, which is a different Gradle module, and `internal` does not
cross a module. The first adapter would have been the moment someone wrote `public val failures`
"just for the database", which is exactly the loophole the design closes.

Whatever answers this becomes the pattern for every aggregate the project stores (sessions, factors,
recovery codes), so it is worth deciding once.

## Decision

**A stored aggregate publishes a `Record` interface and a `writeTo(record)` method.** The interface is
a vocabulary of facts, one method per thing the aggregate can say (`Attempt.Record` has `started`,
`verified` and `failed`). `writeTo` says all of them, in an order the aggregate chooses and documents.
The adapter implements the interface and turns each fact into rows. The aggregate decides what to
say; the record only listens. Nothing is ever read off the aggregate.

**It is brought back by `restore(replay)`**, a factory in the aggregate's companion. The adapter's
replay says the same words into the `Record` it is handed, from the rows. The record that `restore`
passes in is the aggregate's own: it checks that what is being said is a history the aggregate could
have lived (nothing before the start, no factor from before the attempt began, answers in order) and
throws `IllegalStateException` otherwise. The message names the next action. Restoring is therefore as
strict as growing the aggregate normally is, and a hand-edited row cannot produce a state the domain
refuses.

**What is computed is not stored.** `Progress` is derived from an attempt and a policy at the moment
of asking, so it has no `writeTo`.

**A test reads an aggregate the way storage does.** Aggregates have no `equals` and no getters, so a
spec listens to `writeTo` and compares the transcript (`AttemptStory`, `VersionStory` in the
application module's test fixtures). A conformance suite (ADR-046) written that way runs against the
fake and the Postgres adapter alike.

### What the first two applications decided

These follow from the rule but were choices of their own, listed so a reviewer can disagree with any
of them.

- **An attempt knows its purpose, and so does a policy.** ADR-078 already says an attempt has one;
  `Attempt` and `SignInPolicy` now carry it. Without it a stored attempt cannot be resumed under the
  right policy.
- **A policy is stored as a numbered version of a checked policy.** `PolicyVersion` holds a number and a
  `SignInPolicy`, so the only policies that can be kept are ones that passed their bounds.
  `PolicyVersion.restore` checks them again against the bounds the code has *today*: a version saved
  when the bounds were wider is refused loudly rather than judged under numbers the code no longer
  allows.
- **Attempts only grow, and storage keeps that true under concurrency.** `Attempts.save` keeps an
  attempt only if it contains everything already kept, and answers `Superseded` otherwise. The adapter
  locks the attempt's row, reads what is kept at that moment, and compares. Two guesses of a code sent
  together cannot both be recorded as "the first wrong answer", so the failure limit cannot be beaten
  by parallel requests. The primary keys on `(attempt_id, position)` are the last line behind the lock.
  Time only moves forward in an attempt: `withVerified` and `withFailure` refuse a time earlier than the
  start or than one already held, the same rule `restore` applies. Without it a request that lost the
  race and reapplied its own, older time would append a history that `restore` then refuses, and the
  attempt could not be read again. The reapplying caller reads the clock again.
- **Putting a version in force names the version kept, not one built to look like it.** `activate`
  compares what the given version tells with what is kept under its number and refuses a difference,
  so a `PolicyVersion` made by hand cannot put a stored policy in force under a false description.
- **Policy history is append-only, enforced by the database.** Versions, their steps and their
  activations are refused every `UPDATE` and `DELETE` by triggers, and the steps of a version (and the
  kinds of each step) can only be inserted by the transaction that inserted the version, which the
  database records in `made_in_transaction`. A row added to a version already kept would change what it
  demands without a new version or an activation to show for it. Rollback is activating an earlier
  version, which adds a row to `policy_activations`; the version in force is the latest row.
- **The initial policies are data in a migration**, the table of ADR-078 as version 1 of each purpose,
  so there is no moment after a deploy when signing in has no policy. A spec reads them back through
  the domain.
- **The code sets the bounds, the schema does not repeat them.** The checks in the tables refuse only
  what no bound could allow (non-positive numbers). A bound copied into a constraint would need a
  migration to change what a release is meant to change by itself.
- **Instants are kept to the microsecond and limits to the millisecond**, as Postgres keeps them, and
  the comparison that decides `Superseded` is made at that precision, so an attempt saved twice
  without being loaded is not mistaken for two histories.

## Alternatives considered

**B. One named snapshot** (`attempt.snapshot(): Attempt.Snapshot`, a public data class). Less code, and
exactly "every field out, in one bag": any code can call it and start deciding from the fields. The
rule would rest on review, not on the compiler.

**C. Store events and restore by folding them.** A good fit for an attempt, which is a history. But it
is a new way to store state, and it would be tempting to apply it to everything, for a project whose
other aggregates are plain current state. Revisit it if an aggregate needs its full audit trail.

**Serializing the aggregate into a JSON column.** Moves every invariant out of the schema into the
reader, and either needs the fields public or a serializer that reaches around the encapsulation.

**Exposed DAO entities.** Ruled out by ADR-049 for all domain objects, and they would be the very
public-field bag this record exists to avoid.

## Consequences

Each stored aggregate costs one `Record` interface, a `writeTo`, a `restore` and a small replay in the
adapter. That is more code than a snapshot, and it is the price of having no accessor the next change
can reach for.

An aggregate that is stored has two vocabularies to keep in step: the words of its `Record` and the
columns of its tables. A new fact is a new `Record` method, and every implementer fails to compile
until it handles it, which is the same guarantee `Progress.Report` gives.

Not decided here, for the slice that first needs it: the attempt identifier is a UUIDv7 from
`IdGenerator`, which is not a secret (its documentation says so). When sign-in uses it as the thing a
client holds, it may need to be an unguessable value of its own, with only its hash kept. The tables
for accounts, factors, sessions and the security journal, and the author and comment of a policy
version, arrive with the code that first reads them.
