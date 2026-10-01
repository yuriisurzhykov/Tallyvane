# ADR-078. Sign-in is assembled from factors by a versioned policy per purpose

## Status

Accepted.

## Context

The owner's requirement: "a sign-in token" and "schemes" of sign-in must exist as concepts, and
the way someone signs in must not leak into the business logic of signing in. Adding TOTP, later a
passkey or Apple, must not mean rewriting the flow. The combination of factors must be configurable
by an administrator, not compiled in.

The previous attempt hard-coded a "first factor" and a "second factor" as different concepts
(`PendingAuthentication`, `SecondFactorMethod`) and had no policy at all.

## Decision

The model uses the vocabulary of NIST SP 800-63 and Keycloak's authentication flows.

**Factor.** What a person proves themselves with: `google`, `totp`, `recovery_code`; later
`passkey`, `apple`. A factor belongs to an account and moves through *pending* → *active* →
*revoked*. A pending factor protects nothing and does not count.

**Method.** The code that can verify one kind of factor. Every method has the same narrow contract,
*begin* and *verify*, and reports only a fact: *a factor of this kind was verified for this account
at this time*. How it verified it, a redirect to Google or six digits, the rest of the system does
not know. A new method is a new implementation, not a change to the flow.

**Attempt.** The owner's "sign-in token": a short-lived server-side record of one sign-in in
progress, with its purpose, the factors verified so far, the failures so far and its expiry. The
client holds only an opaque identifier. The attempt collects factors until the policy says it is
complete; only a complete attempt can be redeemed for a session (ADR-076, ADR-079).

**Purpose.** Why the person is authenticating: `registration`, `login`, `admin_login`, `step_up`
(confirming a dangerous action). Each purpose has its own policy, so registration and sign-in are
separated by data rather than by code paths.

**Policy.** For each purpose, which combinations of factors are enough, plus the numbers that
govern sessions (ADR-079) and second-factor handling (ADR-082). The next step of a sign-in is
derived from the policy and the account's active factors; nothing in the code says "after Google
comes TOTP".

**Policies live in the database as immutable versions.** A change creates a new version; one
version is active. Rollback is activating an earlier version. History and rollback come from one
structure. Policies change only through `admin.`, every change is journalled with its author and
comment, and activation requires a fresh step-up from the administrator. The admin screen shows
the consequences before confirming ("312 users without TOTP will have to set it up at their next
sign-in").

**The code sets bounds, the policy sets values.** A policy outside the bounds cannot be saved: for
example, idle lifetime between 15 minutes and 30 days, absolute lifetime at most 90 days. An
administrator's mistake, or a captured admin API, cannot set a session to live ten years or remove
the second factor from `admin_login`.

Bounds for a sign-in attempt, agreed with the owner on 2026-10-01 (initial value in brackets):
attempt lifetime 1 to 15 minutes (5), wrong answers before the attempt ends 3 to 10 (5), first
pause after a wrong answer 1 to 10 seconds (1), doubling after each further one (ADR-082). The
bounds of session lifetimes are those above.

**A user can only raise their own bar.** The active policy is the floor. A factor the user enabled
becomes required for that user. Nobody can go below the floor.

**Tightening applies immediately**, including to sessions already issued: sessions store when they
were authenticated and last used, not a precomputed expiry (ADR-079).

**Confirming a dangerous action never asks for less than signing in.** `step_up` has the same
shape as `login`: someone with TOTP confirms with Google and a code, not with either alone.
Otherwise a person who took over the Google account could switch TOTP off with Google alone.

**A policy that would lock people out is handled, not forbidden.** When a policy requires a factor
an account does not have, sign-in completes into a restricted state, "signed in, must set up the
factor", which allows only the setup.

Initial policy values:

| Purpose        | Enough                                                   |
|----------------|----------------------------------------------------------|
| `registration` | `google`                                                 |
| `login`        | `google`, plus `totp` or `recovery_code` if TOTP is on   |
| `admin_login`  | `google` and (`totp` or `recovery_code`), always         |
| `step_up`      | `google`, plus `totp` or `recovery_code` if TOTP is on; not older than 5 min |

## Alternatives considered

**Hard-coded flows per provider.** What the previous attempt did. Rejected because each new factor
would rewrite the flow, and the owner explicitly asked for the opposite.

**Policies in configuration files.** Simpler, rejected by the owner: changing a policy would need a
deploy, and there would be no journal, no fresh confirmation and no rollback.

**Editing a policy in place with a separate audit table.** Rejected: two structures that must be
kept consistent, where immutable versions give both history and rollback from one.
