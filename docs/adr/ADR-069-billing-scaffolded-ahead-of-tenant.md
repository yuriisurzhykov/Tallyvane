# ADR-069. `billing` is scaffolded in Milestone 0, ahead of any paying tenant

## Decision

An empty `billing` module — the standard five-layer template (`contract`, `domain`, `application`, `infrastructure`,
`web`) — ships in Milestone 0, alongside `identity`. It checks access through
`access.grantsOf(userId).allows(Permission.BillingManage)` (ADR-071) from the start. No payment provider is wired, no
UI exists, and no money moves. §20's "enable billing" recipe becomes "add a provider," not "add a module."

## Why

ADR-005 recorded "multi-tenancy from day one, billing — no," reasoning that a scaffold with no consumer is
unreviewable, unused code that YAGNI and this repository's `no-utility-files` rule both refuse. That reasoning still
holds for the *provider*: nothing here changes it, and real integration stays backlog, gated on an actual second
tenant (§21). It stops holding for the *module boundary and the access check*, because the product is positioned as a
SaaS with one tenant today rather than a personal tool that might become one later (§1.4) — a SaaS's billing boundary
is part of what the word "SaaS" means, not speculative infrastructure held in reserve for a hypothetical second
customer.

## Rejected alternatives

**Wait for a second tenant and build the whole module then.** This is ADR-005's original position, and it is not
wrong on its own terms — it answers a different question than the one this decision answers. The cost of waiting is
not code complexity (§20 already shows the eventual recipe is small); it is the shape of every other module's
paid-feature use case, written once now against a real port, or written once now against nothing and rewritten later
against a port that did not exist when the use case was designed.

**A hard-coded `true` where a permission check will eventually go.** Rejected outright: it is not a scaffold, it is a
placeholder that someone has to find and replace by hand later — exactly the kind of thing `Access`/`Grants` (ADR-071,
§6.14) exists to make unnecessary, since every paid-feature use case already calls the same port every other
permission check calls.

**Build the provider integration now too, in sandbox/test mode, since it costs little with a test account.** Rejected:
there is no paid feature yet for it to gate, so its correctness cannot be exercised by anything real — it would be
tested against itself, which is the kind of coverage this repository's own testing discipline treats as worse than no
test at all.
