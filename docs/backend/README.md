# Backend documentation

Two kinds of document, following what `docs/frontend/` already does: numbered
files for a cross-cutting concern, and one file per capability module written
before the module is built.

## Cross-cutting

- [01-observability.md](01-observability.md) — the four signals and which
  question each answers; health checks in depth, including why there are fewer
  of them than there are modules; and what happens to all of it when the
  monolith starts splitting into services.

## Per capability

None yet. [ARCHITECTURE.md](../../ARCHITECTURE.md) sections 4 through 9 hold the
current level of detail — module map, layer rules, inter-module communication,
the full data model and the event vocabulary. These files take over as each
capability is specified in depth.

## Planned

`identity`, `jobs`, `capture`, `applications`, `contacts`, `documents`,
`resume`, `compensation`, `briefing`, `reminders`, `analytics`, `content`,
`mailbox`.

## What each document covers

The capability's responsibility in one sentence, and what it deliberately does
not do. Its published contract: the interfaces neighbours may call and the
events it emits. Its use cases with their transaction boundaries. Its ports and
their implementations. The tables it owns. The invariants it guarantees. And
the decisions taken while designing it, with the rejected alternatives.

The order matters: the contract is written before the internals, because what a
module shows the outside world is the part that is expensive to change.
