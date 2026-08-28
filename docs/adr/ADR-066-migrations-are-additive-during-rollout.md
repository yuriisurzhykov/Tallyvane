# ADR-066. A migration in a blue-green release is additive only; a destructive change waits for its own release

## Status

Accepted.

## Context

`ops/apply.sh --rollout <service>` (§3 of the CD/blue-green plan) brings up the new colour of a
service beside the old one and only then switches traffic — for `app` specifically, both colours
run against the **same** PostgreSQL schema for the whole cutover window, because the database is
not duplicated. [ADR-051](ADR-051-migration-layout-and-ordering.md) already required migrations to
run through a dedicated one-shot command rather than at application startup, precisely because "the
day there are two [instances]" running migrations at startup would race. That settled *when* a
migration runs. It says nothing about what a migration may contain relative to the two versions of
`app` that will briefly coexist once it has run — and blue-green is what makes that coexistence
routine rather than a one-off risk taken during a manual deploy.

A destructive migration turns that coexistence, survivable for the migration command itself, into
an outage for whichever colour is still serving traffic on the old code: a dropped column empties a
`SELECT *` the old code still issues; a column that gains `NOT NULL` rejects an `INSERT` the old code
still sends unpopulated; a `RENAME` breaks every query naming the old identifier. None of these fail
the migration — they fail the *old* colour, silently, for however long it keeps serving traffic
before `apply.sh --rollout` flips away from it.

## Decision

**A migration shipped in a release that rolls out through blue-green is additive only.** A new
nullable column, a new table, a new index are allowed. `DROP TABLE`, `DROP COLUMN`,
`ALTER COLUMN ... SET NOT NULL` on an existing column, and `RENAME COLUMN`/`RENAME TABLE` are not,
in that release.

A destructive change is split across two releases instead of forbidden outright: an "expand"
release adds the new shape and the application starts writing and reading it while still tolerating
the old one; a "contract" release, once the expand release has been running with no old colour left
anywhere, removes what the new shape replaced. This is Fowler's Parallel Change / the industry name
"expand/contract" — not a technique invented here, only adopted for the reason above.

`CREATE INDEX CONCURRENTLY` is deliberately out of this policy's scope. `playground/ddl-locks` and
the persistence skill already established that Flyway holds its own idle-in-transaction connection
open while a migration runs, which blocks a `CONCURRENTLY` build indefinitely — so an index that
must not lock out readers was never something a migration in this repository could do, additive or
not, and this ADR does not change that.

**Enforcement is a Gradle task, not a review reminder.** `tallyvane.migration-policy`
(`backend/build-logic/migration-policy/`) registers `checkAdditiveMigrations`, wired as a
dependency of `arch` — and therefore of `check` — the same way `tallyvane.graph`'s
`validateModuleGraph` already is. It diffs the migration files a release adds or changes against
a base ref (`MIGRATION_POLICY_BASE_SHA` from CI, or the merge-base with `origin/master` computed
at execution time for a local `./gradlew check` run with nothing configured) and fails, by file
and line, on any of the forbidden statements.

Living in `backend/build-logic/` rather than as a standalone CI script was not the first attempt —
an earlier version was a `.github/scripts/*.mjs` file, on the reasoning that this is fundamentally
a git-diff question, not a Konsist-scannable one, so it did not obviously belong to `arch-tests`
either. That reasoning about *what* the check needs was right and stayed; the placement was
wrong, corrected once it became clear a Gradle plugin answers the same git-diff question exactly
as well and additionally makes the check available through `./gradlew check` — the one command
every other backend gate already uses, run locally before a push rather than only discovered
after one. A rule that only review discipline enforces is a rule that survives exactly as long as
every reviewer remembers it on every pull request; a release that has already been tagged and is
rolling out is the wrong moment to discover that a migration was not additive.

## Consequences

A destructive schema change now always costs two releases instead of one, even when nothing else
about it is complicated — the cost this ADR accepts deliberately, because the alternative is an
old colour failing requests for the length of a cutover window rather than a code review comment.

The check reads git history, not the working tree: it can only see what changed relative to a base
ref, so it has no opinion on a migration that already shipped and would be wrong to flag it — the
same distinction [ADR-051](ADR-051-migration-layout-and-ordering.md)'s own migrations already rely on
by never being edited after merge.

A contributor writing a genuinely destructive change now has to say so by splitting it into two
migrations across two releases, which is friction placed deliberately on the harder, riskier path
rather than removed from it.

## Alternatives considered

**Review discipline alone, the rule stated only in this ADR.** Cheaper to build, catches nothing
mechanically. Rejected for the reason given above: a rule enforced only by memory is a rule that
eventually loses to a release that looked routine.

**A schema per colour, migrated and cut over independently.** Removes the coexistence problem
instead of constraining it. Rejected by the source plan already, for cost: duplicating the database
is exactly what per-service blue-green was designed to avoid needing, and PostgreSQL is explicitly
not duplicated elsewhere in this deployment.

**Running the migration only after the old colour is torn down.** Reverses which version is exposed
to the mismatch instead of removing it: the new colour would then start serving traffic against a
schema it has not migrated yet, which is the same failure mode aimed at the other version.

**Blocking on any schema change at all, additive or not, until a two-phase deploy tool exists.**
Simpler policy, but stops blue-green from shipping any schema change whatsoever until that tooling is
built — a cost far larger than the one this ADR actually accepts.

**A standalone `.github/scripts/*.mjs` script**, matching the pattern `baseline-request-guard.mjs`
and its neighbours already use for CI logic that needs to be a real file rather than a line in
YAML. Built first, then corrected: `.github/scripts/README.md` frames that directory around
scripts holding a write-scoped GitHub API token (`lib/actions.mjs`'s whole reason to exist), and
this check touches no GitHub API at all — the pattern matched by external shape (a CI-only
git-diff script) but not by the reason the pattern exists. It also could not be run the same way
every other backend check already is, `./gradlew check`, which the Gradle-plugin form can.
