# Architecture decision records

One file per decision, named `ADR-0NN-short-slug.md`.

The summaries currently live in [ARCHITECTURE.md](../../ARCHITECTURE.md)
section 22 and are being split out into files here. The identifiers are already
load-bearing: the `@ArchitectureException` annotation on the backend and the
`@architecture-exception` directive on the frontend both require an `adr` field
that names an existing file in this directory, and the architecture tests
verify the file is really there.

## What a record must contain

The decision. The reasoning. **The alternatives that were rejected and why** —
this is the part that matters six months later, when the obvious question is
"why didn't they just…". And, where a decision replaced an earlier one, an
explicit statement that it did, rather than a quiet rewrite.

Several records already exist purely to reverse an earlier position. ADR-029
overturns the idea that one colour could serve as both brand accent and warning
signal; ADR-026 overturns the claim that the frontend needs no external
packages. Both were reasonable when written and were disproved by building the
thing.

## Numbering

Sequential, never reused. A superseded record stays in place with a pointer to
the one that replaced it — the history of a decision is as useful as its
current state.
