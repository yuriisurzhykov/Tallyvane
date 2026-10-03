# ADR-092. A dangerous act asks for a fresh proof, and the session remembers it

## Status

Accepted. Chosen by the owner on 2026-10-03 while planning slice 5 (`https://claude.ai/artifact/MPRF3cvNdJJ2hmRWNpQwmr`,
design and diagrams in `docs/backend/02-authentication-slice-5.md`), each time the recommended option: a
confirmation moves a mark on the existing session and does not replace its secret (fork 1); how long a proof stays
fresh is a third number in the session lifetimes (2). Forks 3 and 4 concern TOTP and are recorded in ADR-093, with that slice.

Refines ADR-079 (the fresh factor for dangerous actions, and the new token "whenever trust changes") and closes the
deviation recorded in ADR-090.

## Context

ADR-079 says signing out other devices, disabling TOTP and regenerating recovery codes ask for a factor no older
than five minutes. ADR-090 shipped the first of those without the check, because nothing could give a fresh proof
yet, and noted it as the first thing slice 5 closes. Two questions had to be answered: where "fresh" is decided when
`authentication` does not know sessions exist (ADR-076), and what a confirmation does to the session it confirms.

## Decision

**A session records when its person last proved who they are.** `confirmed_at` is kept next to `authenticated_at`
and starts equal to it, so a person who has just signed in is asked for nothing. It is a separate column because
confirming must not make a session live longer: the absolute lifetime still counts from `authenticated_at`.
`Session.confirmed(at, factors)` moves it forward only, and adds the factors that proved it.

**How long a proof stays fresh is a third number in the lifetimes**, `freshness`, one to fifteen minutes and five to
begin with, in the same versioned, append-only history as the idle and absolute limits (ADR-090). One version, one
rollback and one screen answer for every question about how long a session is trusted, and it is read once per request
with the other two. The bounds are the code's, not a database constraint, as for the others.

**A confirmation updates the session in place.** The secret in the cookie does not change. Rotating it would make a
parallel request of the same page fail with `401` for no reason a person could see, and the thing rotation protects
against, a stolen cookie, is answered by "sign out everywhere else", which is itself guarded. ADR-079's new token still
comes at sign-in and at a second factor completing a sign-in; confirming a dangerous act does not change who holds the
session.

**The edge decides, not the route.** A route declares `Access.SignedFresh`. `SessionCallers` turns the session's
`Freshness` into `Caller.Confirmed` or `Caller.Signed`, and `Gate` answers a stale `Signed` on such a route with
`403` and the problem type `step-up-required` before any route code runs. A route cannot forget to ask, and every one
asks the same way. An undeclared route stays `Signed`, which is closed to strangers by default (ADR-088).

**Confirming is a sign-in's twin.** `POST /google-step-up` begins an attempt of purpose `StepUp`, public like the
sign-in it resembles because it only begins an attempt. Google returns to `/step-up/continue`, and a Google account
nobody here knows is turned back, never made a registration. `POST /step-ups` takes the finished attempt for a signed-in
person, through its own `SignIns.redeemStepUp`: a sign-in cannot be taken as a confirmation nor the other way, so a
confirmation never grants a session and a sign-in cannot confirm another one. `sessions` compares the confirmed account
with the session's own; another account's confirmation is spent all the same and answered `403`.

**Guarded in this slice:** `DELETE /device/{id}` and `DELETE /other-devices`. Not guarded: `DELETE /session` (signing out
here only takes access from whoever holds the cookie, and a stale session must still be able to), renaming, listing.

## Alternatives considered

**Replace the session's secret on every confirmation.** Rejected for the reason above; it also makes the cookie a
moving target for every parallel request.

**A constant, or a field of the `step_up` sign-in policy, for the freshness.** A constant needs a release to change. The
policy is about which factors a purpose demands, not how long a proof lasts, and it is read by `authentication`, which
cannot see a session's age.

**Each route asks `sessions` whether the caller is fresh.** Works, and is forgotten by the first route written in a hurry.

## Consequences

A person whose proof is older than five minutes meets `403 step-up-required` on the two guarded calls and, in the
console, a window that confirms with Google and repeats the call. The window uses the existing re-sign-in popup. TOTP
adds a second step to the same flow in slice 5b. The migration adds `sessions.confirmed_at` and
`lifetime_versions.freshness_millis`; `confirmed_at` is nullable, as ADR-066 asks of a release that rolls out beside the one before it, and a null is read as
`authenticated_at`; existing versions get five minutes.
