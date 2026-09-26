# ADR-070. Résumé bullets are a representation over Career Memory blocks, not a parallel table

## Decision

`resume.bullets` and `resume.experiences` do not exist. A résumé version selects `career.experience_blocks` (optionally
overriding the generated bullet text) through `resume.version_blocks`; employment metadata that used to live in
`resume.experiences` — company, title, location, dates — moves unchanged into `career.positions`. This is not a
migration path bolted onto an existing `resume` module; it is how `resume` is built the first time, because Career
Memory (Milestone 1) now exists before `resume` does (Milestone 10).

## Why

ADR-033 already established the principle this decision applies: a bullet's wording is a generated representation of
a fact, never written back to `career`. What ADR-033 could not do, at the time it was written, was make `resume`
depend on `career` directly, because `resume` was Milestone 7 of a plan that shipped with no `career` module at all —
ADR-033 described the target shape ("`resume.bullets` gets an optional `source_block_id` column later") as a Phase 2
migration onto data that would exist only afterward. Moving Career Memory ahead of `resume` in the milestone order
(ADR-068) removes the reason for that two-step: there is no earlier `resume.bullets` for a later `career` to retrofit
into, so the direct reference is simply how the schema is written the first time.

## Rejected alternatives

**Keep the migration path ADR-033 described, applied at Milestone 10 instead of a later phase.** Rejected as
unnecessary complexity: a migration exists to reconcile two versions of a schema that were both real at different
times. Here the earlier version — `resume.bullets` with no `career` reference — is never built at all, so there is
nothing to reconcile.

**Leave `resume.experiences` where it is and have `career.experience_blocks` reference it instead of the reverse.**
Rejected: `career` explicitly does not know how its output is used — matching and opportunity read from `career`, not
the other way around (§6.15). Making a fact module reference a presentation module inverts that dependency for no
benefit, and would make an employment position exist only in service of rendering a résumé, when Career Memory's own
matching and Opportunity Score need the same position data whether or not a résumé is ever generated from it.

**Keep both tables and reconcile them with a database view.** Rejected for the same reason this document's own
ADR-045 rejects a view as the default answer to a cross-schema read elsewhere: a view binds the reader to the owner's
physical columns with no gate that would catch drift between the two sides, because the join lives in SQL where
neither side's tests can see it.
