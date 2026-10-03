# ADR-090. Devices are sessions, and session lifetimes are versions owned by `sessions`

## Status

Accepted. Refines ADR-078 (where the session numbers live) and ADR-079 (device list, revoking).
Records one deviation from ADR-079, closed by ADR-092.

## Context

ADR-079 promised that a person can see where they are signed in and sign a device out, and that
lifetimes are computed on every request from policy. Building it raised four questions: what a
"device" is, where the lifetimes are kept, who ends the sessions of an account that is deleted, and
whether revoking waits for a fresh factor.

## Decision

**A device is a session.** One browser holding one cookie is one row; there is no separate device
table to drift from the sessions. The list is the person's own sessions, newest use first. A session
past its idle or absolute lifetime is filtered out of the list but not purged by the request (a read
does not write).

**A device is described by parts, not by a sentence.** The record keeps the browser, the platform and
a mobile flag, taken from the `User-Agent` when the session opens, plus a name the person gave it. The
`User-Agent` cannot tell two Chromes on the same operating system apart, so the name is the one thing
that can: `PUT /device-names/{id}`. A later native client adds its own browser/platform values without
a schema change. The words shown to the person are chosen at the edge (`DeviceWords`), never stored.

**Lifetimes are versions owned by `sessions`.** `lifetime_versions` is append-only (a trigger refuses
update and delete, the pattern of the `authentication` policy tables), `lifetime_activations` says
which version is in force for a client type. Bounds stay in code (idle 15 minutes to 30 days,
absolute up to 90 days and not below idle); values are data (ADR-078). They are read on every request,
one query for all client types, and only when a session was found. Tightening a version therefore
reaches existing sessions at their next request. A loosened version reaches them too, because the
cookie is told the longest the code allows (90 days) and not the value in force when it was issued;
the server alone decides when a session is over. Version 1 for the browser is one day idle, seven days
absolute. The admin screen that activates a version is slice 7.

**The use case takes the cookie secret and looks the session up again.** The list, revoke, rename and
sign-out-others cases call the same `Recognition` that `Authenticate` uses, so there is one place that
decides whether a secret stands, and no call attribute carries a session from the edge to a use case.

**Four route bases, none under `/sessions`.** The edge opens a public module's whole path prefix
(ADR-088), and a base is one segment unique across modules, so the device routes are `GET /devices`,
`DELETE /device/{id}`, `PUT /device-names/{id}` and `DELETE /other-devices`. A malformed or foreign id
answers `404 no_such_device`; the account is part of every by-id statement, so one account cannot
learn that another's session exists.

**A deleted account loses its sessions.** `identity` publishes `AccountDeleted` through its contract;
`sessions` subscribes and revokes every session of that account. `platform:events` is a minimal
synchronous bus: the subscriber runs in the publisher's transaction, so the account and its sessions
go together or not at all. No code deletes an account yet; the path is tested with a test publisher.

## Deviation from ADR-079

**Closed by ADR-092 (slice 5a):** `DELETE /device/{id}` and `DELETE /other-devices` now ask for a fresh proof. What follows is the record of the deviation as it stood.

ADR-079 says signing out other devices asks for a step-up when the session's authentication is older
than the fresh-factor window. This slice does not check it. The factor-freshness policy and the
step-up flow arrive in slice 5, which adds the check to `RevokeDevice` and `SignOutOthers` together
with the other dangerous actions. Until then revoking needs only a live session. The deviation is
noted in `openapi.yaml` and is the first thing slice 5 closes.

## Alternatives considered

**Lifetimes owned by the policy module.** One home for every versioned number, but `sessions` would
read a table in another module's schema on every request, against the module rules (ADR-076).

**A prebuilt device string.** Cheaper to show, impossible to re-word or to translate, and it cannot
carry the native client later.

**An asynchronous event bus.** Account and sessions could diverge on failure, and there is no queue to
justify yet; the interface does not change if one is added.
