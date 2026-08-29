# ADR-065. Three subdomains, three applications

Supersedes the part of ADR-011 that ADR-032 left standing. ADR-032 amended
ADR-011 for the admin surface only and was explicit that the reasoning for
the other pair was unchanged: *"`tallyvane.com` and `app.tallyvane.com`
remain one Next.js process (`frontend-web`), because ADR-011's reasoning for
this pair did not change."* It has changed. The console's own requirements —
not an operational preference — are what changed it.

## Decision

`app.tallyvane.com` (the console) moves out of `frontend-web` into a new,
independent workspace member, **`frontend-app`**. `frontend-web` narrows to
`tallyvane.com` only (blog/landing/docs/changelog/legal).

The subdomain-to-application map is now one-to-one:

- **`frontend-web`** — `tallyvane.com`. Public, server-rendered, tag-cached.
- **`frontend-app`** — `app.tallyvane.com`. The console: server-rendered
  where a first paint matters, with Server-Sent Events for live data,
  described below.
- **`frontend-admin`** — `admin.tallyvane.com`. Unchanged; ADR-032's
  isolation reasoning for admin is untouched by this decision.

`frontend-shared` is consumed by all three applications now instead of two —
theme, API client and string engine are exactly as necessary for a
console with its own server rendering as for its siblings. Whether
`frontend-app` also consumes `content-kit` is left open by this decision:
nothing in the console's current scope (`today`, `pipeline`, `jobs`,
`contacts`, `resume`, `analytics`, `settings`) renders a content block, so
adding the dependency now would be speculative. Neither package changes
shape either way, since both were already designed against the full slice
map in ARCHITECTURE.md §12.5, not against how many applications import them —
so this is a decision that costs one `package.json` line whenever it's
actually needed, not one that has to be made here.

## Why ADR-011's core claim stopped holding for this pair

ADR-011's argument was specific: *"the CMS made server rendering mandatory
for the public part; since a Node runtime is needed anyway, a separate SPA
next to it becomes a redundant entity."* That argument is a statement about
*necessity*, not about *desirability* — it says a second process buys nothing
because the first one already exists and the second one's workload doesn't
need anything the first one lacks. Two facts remove that premise for the
console specifically:

**The console now has its own server-rendering requirement, not a borrowed
one.** ADR-012's original framing — *"the private part is a thin client, not
a BFF; console and admin talk to Ktor directly from the browser"* — described
a console with no first-paint requirement and no per-request server logic.
The real console needs partial SSR/SSG (a dashboard's first paint should not
wait on a client-side fetch) and Server-Sent Events for live updates. Neither
requirement is satisfied by "a Node runtime happens to exist for someone
else's pages" — they are the console's own reasons to run a server, independent
of whatever `frontend-web` needs.

**A shared process means a shared memory ceiling, and the two surfaces no
longer have comparable load profiles.** `tallyvane.com` is public,
anonymous, cache-friendly — most requests should be answered from the tag
cache without touching Ktor at all. `app.tallyvane.com` is one authenticated
owner, but with open SSE connections that hold a socket and a chunk of memory
for as long as the tab is open, plus whatever partial SSR does per request.
A single `mem_limit` for both means an SSE-heavy console session and a
cache-miss storm on the blog compete for the same ceiling, and `docker
compose`'s per-service memory isolation — the exact mechanism that already
protects `db` from `app` — cannot be brought to bear on two surfaces sharing
one container.

Once server rendering is something the console needs for its own sake, ADR-011's
"redundant entity" framing is no longer available: the second process is not
redundant, it is where the console's own workload actually lives.

## Server-Sent Events through the existing tunnel

This is not a new operational component. The console's `EventSource`
connection goes browser → nginx → Ktor directly, the same same-origin `/api/*`
path every other console request already takes (ADR-032) — it never passes
through `frontend-app` at all, consistent with ADR-012's thin-client model,
which this decision does not touch for API calls, only for rendering.

Two conditions make it reliable behind Cloudflare's proxy, verified against
Cloudflare's own documented behaviour rather than assumed: Cloudflare closes
a proxied connection that has been silent for 100 seconds (not a cap on total
duration — connections have run 30+ minutes when data keeps flowing), so
Ktor's SSE endpoint sends a heartbeat comment on an interval shorter than
that; and nginx must not buffer the response for that location
(`proxy_buffering off`), or events queue in nginx's buffer instead of
reaching the browser as they're produced. Both are `platform:http`/nginx
configuration, tracked separately from this decision — they apply regardless
of which container terminates the console's HTML.

## Rejected alternatives

**Keep one process, route by `Host` in `proxy.ts` (this decision's own first
draft).** Cheapest change: no new workspace member, `frontend-web/proxy.ts`
rewrites `app.tallyvane.com` requests into a `(console)` route group,
exactly the shape ADR-011 originally specified. Rejected once the console's
resource profile stopped being hypothetical: this still leaves both surfaces
sharing one `mem_limit`, one process, one crash domain — an unhandled
exception rendering the console takes the public site down with it, and
vice versa. The admin split (ADR-032) was justified by a security property a
route group can't express; this split is justified by a resource-isolation
property a *shared container* can't express, which is a different argument
but lands on the same shape of answer: a missing dependency edge and a
separate container, not a runtime branch inside one.

**Make the console a static export, sharing nothing with a live server.**
Considered because a purely client-rendered console (ADR-012's original
description) doesn't obviously need a server at all. Not viable, and not
because of preference: Next.js's `output` mode is set for a whole
application, not per route, and `output: "export"` supports neither
server-side rendering nor Server-Sent Events — both of which the console now
needs. This alternative was foreclosed by the console's own requirements
before it could be compared on cost.

**A dedicated `api.` hostname for the SSE endpoint, to keep it out of the
console app's proxy path.** Unnecessary: the SSE connection never touches
`frontend-app`'s process in the design above, so there is no proxy path to
route around. Introducing a fourth hostname would reopen the CORS question
ADR-032 closed for exactly this reason — same-origin `/api/*` on the hostname
that already serves the page.

## Consequence: the memory budget gets a third line, and needs re-measuring

ARCHITECTURE.md §16.2's two Node lines become three. `frontend-web` drops
from 320 MB to 256 MB — it sheds the console's SSE connections and dashboard
rendering, keeping only cache-friendly public pages. `frontend-app` is
budgeted at 384 MB — SSR plus held-open SSE connections is a heavier and
less predictable profile than either sibling. `frontend-admin` is unchanged
at 192 MB; nothing about this decision touches it.

All three numbers are estimates, same epistemic status ADR-032 gave
`frontend-admin`'s 192 MB: to be replaced by a `docker stats` reading once
each application has real traffic, not adjusted by guesswork before then.
The real machine has 3.768 GiB, not the 2 GB §16.2 was written against
(`ops/README.md`, measured 2026-08-27) — recalculating the whole table for
the real number is tracked as an open item in
`backend/.plans/backend-infra-cache-wiring.md`, and this decision does not
attempt it; it only adds the row the table will need.

`frontend-web/app/proxy.ts` and the `(console)` route group planned in
ARCHITECTURE.md §12.2 move to `frontend-app` unchanged in spirit — same
routes (`today`, `pipeline`, `jobs/[id]`, `brief/[applicationId]`,
`contacts`, `resume`, `analytics`, `settings`), same FSD layering — but
`frontend-app` needs no `Host`-based `proxy.ts` of its own: nginx already
resolves which container a hostname reaches, so there is nothing left for an
in-app proxy to decide.
