# ADR-089. The console talks to the API through a chain of one-job transports; the session ending is a dialog, not a redirect

## Status

Accepted. Chosen by the owner on 2026-10-02 while planning slice 3c (`/mnt/project-files/auth-design/slice-3/05-plan-3c.md`):
the real backend only, no mock (fork 1); `openapi-typescript` types and our own transport (2); the re-sign-in
dialog is built now, not later (3); the session is checked on the server in the console layout (4); the
address to return to waits in `sessionStorage` (5). Also from the owner: the local stand is the production
compose file with a different `.env`, and the transport must not grow into one object that knows everything.

## Context

ADR-064 deferred a typed client until a frontend called the API; with `/login`, `/welcome` and the console
gate, it does. ADR-084 decided that an ended session is handled in the transport; ADR-086 that every unsafe
request carries an `Idempotency-Key`. Both are rules about *every* call, so they cannot live in each feature.
The risk is the usual one: a single `ApiTransport` that adds keys, parses problems, retries, reauthenticates,
logs, and slowly learns every endpoint.

## Decision

**A chain of decorators, each with one job and at most two collaborators.** Outermost first:
`IdempotentTransport` (fixes a key once, before anything can repeat the request), `ReauthenticatingTransport`
(on `session-expired`, waits for the handler, repeats once), `ProblemTransport` (a `4xx`/`5xx` becomes a
`ProblemError`), `FetchTransport` (one `fetch`, no opinions). `Api` is a facade of four methods typed from
`docs/openapi.yaml`; it builds a request and hands it down. The order is the design: the key must exist before
a repeat, and a failure must be an exception before anything can decide to repeat it. A concern added later is
a new decorator, not a new branch.

**Types are generated, not written.** `pnpm --filter frontend-shared api:generate` writes
`shared/api/generated/schema.d.ts` from the specification; `api:check` (part of `arch`) fails when the committed
file is stale, the same pair as the design tokens. An endpoint a feature calls that the specification lacks is a
type error.

**The session ending is a dialog.** `ReauthenticatingTransport` knows only an `ExpiredSessionHandler`. The
console supplies `ReauthSession`: it turns "someone must sign in again" into state a `Dialog` shows. Google opens
in a small window (the address is fetched as the dialog appears, so the click opens it synchronously and a
pop-up blocker allows it; if it is blocked anyway the same address is offered as a link). Google's pages cut a
window off from its opener (COOP), so the window tells the page behind it through a `BroadcastChannel`, and
finds out it is that window from a short-lived note in `localStorage`. When the page hears, it asks `/me`; the
same person: the waiting requests are repeated; someone else: the page reloads, because what was on it was not
theirs. `Dialog` is added to `frontend-shared` as the one exception to "modals are banned" (COMPONENTS.md):
a blocking interruption that cannot be a form on the page, controlled and with no close button.

**The gate is on the server.** The console layout asks `/me` with the visitor's own cookie, through nginx and
with the visitor's `Host` (`TALLYVANE_API_INTERNAL_URL=http://nginx`), so the request is routed as the
browser's is. Nobody: redirect to `/login?return=<path>` (a `proxy.ts` hands the layout the path). The sign-in
page keeps the path in `sessionStorage` through the trip to Google and `isSafeRelativePath` checks it on the
way out. `POST /sessions` is sent once per page (React runs effects twice in development); a `409` without
`Retry-After` means it was already done, so the client reads `/me` (ADR-086). Every press of "Create account"
is a new intention and gets a new key.

**One compose file.** `ops/docker-compose.yml` is what the server runs and what a laptop runs. `ops/local/`
adds only builds from the checkout, port 8080 and one nginx block for `localhost` (Google refuses
`app.localhost` as a redirect URI). The services' settings are the same names in a different `.env`.

## Consequences

The console cannot show a page to a signed-out visitor, and a server outage shows as an error page rather than
a login prompt. The console's health check is `/login`, not a console page, so a rollout does not stall on the
server. `ReauthSession` and the popup handoff are covered by running the flow in a browser, not by unit tests:
`frontend-app` has no test runner yet.
