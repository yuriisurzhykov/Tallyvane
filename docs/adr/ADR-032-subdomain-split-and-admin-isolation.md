# ADR-032. Three subdomains, two applications, two shared packages

Amends ADR-011 rather than overturning it. ADR-011's core claim — one Node
runtime is needed for public SSR anyway, so a second one next to it is a
redundant entity — still holds for the pair it was actually about: the public
site and the console. It stops holding once "the admin surface must be
network-isolated" becomes a real requirement, because network isolation
between two things sharing a module graph is a policy, not a fact.

## Decision

`tallyvane.com` (blog/landing/docs/changelog/legal), `app.tallyvane.com`
(console) and `admin.tallyvane.com` (CMS admin) are three subdomains served
by **two** Next.js applications, not one and not three:

- **`frontend-web`** serves `tallyvane.com` and `app.tallyvane.com`. These two
  evolve together, are built by the same person in the same sitting, and
  share nothing with admin except what's described below — there is no
  reason to pay for a third process to separate them.
- **`frontend-admin`** serves `admin.tallyvane.com`. A separate pnpm workspace
  member that does not depend on `frontend-web`, so a developer cannot import
  console-only code into admin, or vice versa, by accident — the import
  simply does not resolve. This was the explicit requirement: *"мы физически
  когда пишем код — не смогли приплести какую-то функциональность из app в
  admin, и наоборот."*

Two new workspace packages hold what both applications genuinely need,
cross-checked against the full slice map in ARCHITECTURE.md §12.5 rather than
assumed:

- **`frontend-shared`** — the entire former `frontend/src/shared`: theme,
  API client, string engine, `lib`, `config`, and the block-registry
  *contract*. Moved verbatim; by the `shared-has-no-domain` rule it already
  carried zero business meaning, which is what made the move safe without
  first negotiating what belongs to which app.
- **`content-kit`** — `entities/content-page`, `entities/media-asset`,
  `widgets/block-renderer` and every `widgets/*-block` slice. Kept as a
  *second* package rather than folded into `frontend-shared`, because unlike
  the design system this one does carry business meaning (the content
  domain), and merging the two would force a future non-content consumer of
  the design system to also depend on the block-type system for no reason.

Ktor is unaffected: still one process, still the single API both apps call
directly from the browser (ADR-012 unchanged). The tunnel's `/api/*`,
`/calendar/*`, `/media/*` path rules are replicated under both
`app.tallyvane.com` and `admin.tallyvane.com` rather than introducing a
dedicated `api.` hostname, so every browser call stays same-origin and no
CORS configuration — which exists nowhere in this system — needs to.

Admin's network isolation is Cloudflare Access on the `admin.tallyvane.com`
hostname, checked before any request reaches `frontend-admin` at all, plus a
session cookie scoped to that hostname alone (no `Domain=.tallyvane.com`) —
logging into `app.*` grants nothing on `admin.*`, even for the owner.

## What was written before, and why it stopped being enough

ADR-011 assumed the only three surfaces the frontend would ever need to
distinguish were public/console/admin, and that a route group was sufficient
distinction between them. That was true right up until the admin surface
needed a security property route groups cannot express: unreachability, not
just a different URL. A `Host`-header check in a shared build (the first
alternative considered — see below) still leaves every admin route handler
compiled into the same process the console runs in; the isolation would have
been enforced by a runtime `if`, not by the absence of code.

## Rejected alternatives

**An env-var-gated single build.** One Next.js build, run as two processes
differentiated by a `SURFACE` environment variable, with `proxy.ts` returning
404 for routes that don't belong to the current instance. Cheaper — no new
workspace members, no package boundary to design — and it was the first
answer given in this conversation. Rejected on inspection: this app's Next.js
layer is a thin client (ADR-12) that holds no secrets and makes no
authorization decisions, so the actual security boundary was always Cloudflare
Access, the per-hostname cookie and Ktor's `content.manage` capability check —
none of which a second *process* running the *same code* strengthens. Worse,
it doesn't deliver the property that was actually asked for: the compiled
admin route handlers are still present in the "web" instance's module graph,
reachable by anything that could bypass the `Host` check from inside that
process. A real boundary needed to be a missing dependency edge, not a
runtime guard.

**A VPN (Tailscale/WireGuard) instead of Cloudflare Access for admin.**
Rejected because it adds an operational component that doesn't exist anywhere
else in this system. The entire point of the VPS + Cloudflare Tunnel choice in
ADR-006/ADR-011 was that no inbound port is ever opened and no second platform
needs operating. Cloudflare Access attaches an identity check to a hostname
already served through the same tunnel — zero new infrastructure. A VPN would
have reintroduced exactly the operational cost ADR-011 was written to avoid,
to solve a problem Access already solves for free.

**A dedicated `api.tallyvane.com` hostname for Ktor, with CORS.** Considered
because it's the more "normal" shape for a multi-frontend architecture.
Rejected because it buys nothing here and costs a mechanism (CORS
configuration, credentialed cross-origin requests) that has never existed in
this system and that ADR-012 was written partly to avoid needing. Replicating
the existing path-based tunnel rule under each frontend hostname keeps every
call same-origin.

**One shared package instead of two.** Simpler to set up, but it would have
meant every future consumer of the design system — including, eventually, the
mobile client ADR-012 keeps the API honest for — pulls in the content-block
system too, for no reason but extraction-time convenience. Splitting along
"carries business meaning or doesn't" (the same test `shared-has-no-domain`
already applies everywhere else) cost one extra `package.json` and kept the
two concerns separable.

## Consequence: the memory budget moves

§16.2's Node line (320 MB, one process) becomes two lines: `frontend-web`
(kept at 320 MB — same traffic profile as before) and `frontend-admin`
(budgeted at 192 MB — single owner, low concurrency, smaller route tree; an
estimate, to be replaced with a measured number once the app is real).
Reserve for the OS and file cache shrinks from ~640 MB to ~464 MB. Still under
the 2 GB ceiling in §1.5, with less room than before — worth re-measuring
honestly once `frontend-admin` has real pages, per the "quote measured
numbers, never remembered ones" rule.
