# ADR-097. The admin site is a second door onto the same sign-in, with its own kind of session

## Status

Accepted. Chosen by the owner on 2026-10-07 while planning slice 7 (`https://claude.ai/artifact/D7grVrDNLaPRmTzxKZBBxc`;
diagrams in `docs/backend/07-authentication-slice-7-admin.md`): an administrator is a row in a table (fork 1, A), a
policy that would lock people out is forbidden and no session "only for setting up" is built (fork 2, B), the
Cloudflare Access header is not checked in Ktor (fork 5, A). Fork 3 (author and comment as columns) is built with the
versions API and recorded with it. Refines ADR-078 (the `admin_login` purpose), ADR-079 (client types) and ADR-080 (origin
check and cookies per host).

## Context

ADR-078 made `admin_login` a purpose and put a policy for it in force from version 1: Google, then TOTP or a recovery
code, from everyone. Nothing begins that purpose. ADR-032 gave the admin site its own host, behind Cloudflare Access, and
ADR-080 made the session cookie host-only, so the two sites cannot share a session. What is missing is the administrator
(nothing in the code says who one is) and the way the existing sign-in routes serve a second host: the origin check, the
redirect URI registered with Google, the page the browser lands on and the kind of session all assume one site.

## Decision

**An administrator is an account with a row in `identity.admins`.** `identity` offers `Admins.isAdmin(account)` through its
contract; `sessions` and `authentication` read it. The right is given by a script, `ops/grant-admin.sh <email>`, which
inserts the row; no HTTP route gives it, so a captured admin session cannot make another administrator. The check runs
when a session is opened and, for admin routes, in every use case, so taking the right away works from the next request.

**One sign-in, two doors.** The same routes serve `app.` and `admin.`. `Surface` (`App` or `Admin`) lives in
`platform:kernel`; `platform:http` reads it from the request's `Host` (`Surfaces`). The door chooses what a sign-in is
for: `login` on `App`, `admin_login` on `Admin` (`step_up` and the confirmation of a dangerous act work on both), the
redirect URI Google is given and trades the code against, and the page the browser is sent back to. A second redirect URI,
`<admin origin>/api/v1/google-return`, is registered with Google. The attempt cookie `__Host-attempt` is host-only, so an
attempt begun on one door cannot be continued on the other.

**The administrator's session is its own `ClientType`, `Admin`.** It has lifetimes of its own, versioned like the
browser's (ADR-090): by default one hour idle, eight hours absolute, five minutes of freshness. The server compares the type
of a session with the door of the request, so an `Admin` session does not work on `app.` and a `Browser` session does
not work on `admin.` even if the secret is carried across by hand; a session that does not fit is answered like an ended
one and is not deleted. `OpenSession` for the `Admin` door redeems only an `admin_login` sign-in
(`SignIns.redeemAdminLogin`) and refuses an account that is not an administrator with `403 forbidden`.

**Forgery is refused per door.** An unsafe request must carry the `Origin` of the door its `Host` names, so the origin of
`admin.` is not accepted on `app.` and the other way round (ADR-080 held one origin).

**Nobody registers through `admin.`.** A Google account unknown here is sent back to `/login?problem=refused` and no
registration is begun.

**A person without TOTP is not let in, and nobody is locked out by a policy.** `admin_login` demands a second factor from
everyone, so an administrator without one gets the `restricted` state from `GET /sign-in` and is told to turn it on at
`app.`. For every other purpose the bounds check refuses a step that demands a second factor from everyone (the version
API of slice 7b reports it as `would-lock-people-out`), because a session "only for setting up" does not exist.

## Alternatives considered

**An allowlist of accounts in an environment variable.** No table and no script, but a change needs a restart, the id of
the account has to be found first, and nothing records since when someone was an administrator. Rejected by the owner.

**A session "only for setting up" for people a policy demands a factor from.** Makes any policy possible and the
"312 people will have to set up TOTP" preview true, but it touches the edge (`Gate`, `Caller`), the sessions schema and the
client flow for one act. Rejected by the owner; it can be built when something other than `admin_login` needs it.

**Separate sign-in routes under an admin prefix.** Every route of the sign-in would exist twice and would have to be
fixed twice. Rejected: the door is data about the request, not a second set of routes.

**The administrator's session as a `Browser` session with a flag.** One type of session on both doors, which is what
the type is there to prevent: lifetimes and the door could not be told apart by the type.

**Checking Cloudflare's signed `Cf-Access-Jwt-Assertion` header in Ktor.** A second line if Access is ever switched off,
paid for with a dependency on Cloudflare's public keys, two more settings and a stub in local development. Rejected by the
owner (ADR-032 made Access the network layer and this the application layer).
