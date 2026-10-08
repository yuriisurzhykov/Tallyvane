# Backend documentation

Two kinds of document, following what `docs/frontend/` already does: numbered
files for a cross-cutting concern, and one file per capability module written
before the module is built.

## Cross-cutting

- [01-observability.md](01-observability.md) — the four signals and which
  question each answers; health checks in depth, including why there are fewer
  of them than there are modules; and what happens to all of it when the
  monolith starts splitting into services.

- [02-authentication-slice-5.md](02-authentication-slice-5.md) — step-up (a fresh
  factor for dangerous actions) and TOTP: the flows, module dependencies and
  classes the code of slice 5 is written from.

- [03-authentication-slice-5b-totp.md](03-authentication-slice-5b-totp.md) — TOTP on the backend:
  routes, the sign-in, enabling and recovery-code flows, module dependencies,
  classes and tables of slice 5b, with what changed while it was built.

- [04-authentication-slice-5c-totp-screens.md](04-authentication-slice-5c-totp-screens.md) — the screens
  of TOTP in the console: the sign-in and confirmation flows, the Security card, module
  dependencies and classes of slice 5c.

- [05-authentication-slice-6-journal.md](05-authentication-slice-6-journal.md) — the security journal:
  how an entry is written and read, module dependencies, classes and the table of slice 6.

- [06-authentication-slice-6b-activity-page.md](06-authentication-slice-6b-activity-page.md) — the Activity
  page of the console: how the journal is read a page at a time, module dependencies and classes of slice 6b.

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

- [07-authentication-slice-7-admin.md](07-authentication-slice-7-admin.md) — the administrator: signing in on the
  admin site, and editing the sign-in policy and the session lifetimes: flows, module dependencies and classes of
  slice 7.
