# ADR-098. Policies and session lifetimes are edited as kept versions through an API of the administrators' site

## Status

Accepted. Chosen by the owner on 2026-10-07 while planning slice 7 (`https://claude.ai/artifact/D7grVrDNLaPRmTzxKZBBxc`;
diagrams in `docs/backend/07-authentication-slice-7-admin.md`): author and comment are columns on the version and
activation tables (fork 3, A). Builds on ADR-078 (versioned policies, bounds), ADR-090 (lifetimes are versions owned by
`sessions`), ADR-092 (fresh proof) and ADR-097 (the administrators' door).

## Context

ADR-078 and ADR-090 made the sign-in policy of each purpose and the lifetimes of each kind of client into immutable
versions, the active one being the last activation. Nothing could change them but SQL. Slice 7 gives an administrator
screens for it, and the screens must not be able to lock people out, nor to change what is in force without a trace.

## Decision

**A change is two acts.** `POST /policy-versions` and `POST /lifetime-versions` keep a new version and change nothing
for anyone. `POST /policy-activations` and `POST /lifetime-activations` put a kept version in force; a rollback is the
activation of an earlier version. There is no draft on the server: a preview (`GET /policy-previews`,
`GET /lifetime-previews`) is of a kept version, so a new version and a rollback are previewed by one call. Writes only
append (the append-only triggers of ADR-078 hold).

**Who and why are kept with the row.** A version has `made_by` and `comment`, an activation `activated_by` and `reason`,
as nullable columns that are both present or both absent and 1 to 500 characters (migrations are additive, ADR-066).
The rows migrations made carry neither, and the API says so by leaving the fields out. The author is an account
identifier, not a foreign key, since accounts belong to another schema.

**The bounds stay in code and are reported whole.** A draft (`PolicyDraft`, `LifetimeDraft`) is checked and tells a
`Report` every bound it breaks, so a form is corrected once; the answer is `422` with `errors[{field, code}]` and the
sentences to show. A policy that would demand a second factor from everyone is refused for every purpose but
`admin_login` (`would-lock-people-out`, ADR-097). The use cases say the faults in words (`PolicyWords`,
`LifetimeWords`); the web layer only maps them to fields.

**An activation may not leave no administrator able to sign in.** Activating a version of `admin_login` that none of the
administrators could pass, given the factors they have set up now, is refused: `422` with
`would-lock-out-every-administrator` on `number`. The count comes from the `EnrollmentCensus` port (one grouped query)
and `Admins.all()`; the preview reports the same numbers (`people`, `cannot_pass`). A tighter lifetime needs no such
guard: the people it ends sign in again.

**The preview of lifetimes counts live sessions.** `would_end` is how many live sessions of the kind of client a
version would end at once, judged by the same rules that decide a request, on the clock of the request.

**Every route is of the administrators' site and says so.** `RouteModule.door` names the surface a route belongs to and a
route-scoped plugin in `platform:http` answers `404` anywhere else, so the rule is the module's declaration and not a
spelling of a path. Each use case checks `Admins.isAdmin` again, so taking the right away works from the next request.
Activations are `SignedFresh` (ADR-092).

**The administrators' lifetimes are version 1 in a migration.** ADR-097 left the row to the code because the release
rolling out beside 7a could not read an unknown kind; that release does, so the 7b migration inserts it with the same
numbers.

## Alternatives considered

**`409` for the lock-out.** It reads as a conflict with the current state, and an account could set TOTP up between the
preview and the activation, so the same request would later succeed. Rejected: the request is well formed and one
vocabulary (`422`, `errors` on a field) serves every refusal the screen has to show.

**Previewing an unsaved draft.** Lets an administrator look without leaving a version behind, but needs the whole body
in a `GET`, or a `POST` that writes nothing, and a second path through the bounds check. Rejected: a kept version costs
nothing, changes nothing until activated, and keeps one code path for a new version and a rollback.

**Authorship in a separate audit table.** One more table to join for every row the screen shows, and an activation
without its record is possible. Rejected by the owner (fork 3): one row says what, when, who and why.

**One shared `Change` and `Fault` in the kernel.** Saves two small copies, adds a kernel type that two modules'
vocabularies depend on. Rejected: each module keeps its own words for its own faults.

**Checking the door in each route.** Every new admin route would have to remember. Rejected for the plugin: a route
that declares no door is open to both, and one that declares it cannot be mounted without the guard.
