# ADR-084. An expired session is handled by the API transport, and no feature knows it can happen

## Status

Accepted.

## Context

A person comes back to a tab after a day and presses "Save". The session expired (ADR-079), and the
server answers 401. Sending them to the sign-in page loses whatever they typed. Teaching every
feature to handle 401 spreads one concern across the whole front end.

The sign-in screens themselves follow from the earlier records: one "Continue with Google" button
(a new person lands in registration, an existing one in sign-in, since there is only one way to
register), a `/welcome` step on registration with the name and an explicit consent to the privacy
policy (the product stores personal data, including that of third parties), and English text through
the string layer (§13), never literal strings in markup.

## Decision

**The session-expired answer is a problem type of its own.** A 401 with the RFC 9457 type
`session-expired` (§11.6) is distinguishable from every other refusal.

**`shared/api` turns that answer into a call to one registered handler**,
`reauthenticate(): Promise<void>`. When it resolves, the transport repeats the original request once.
Concurrent 401s wait for the **same** promise (single-flight), so one dialog opens, not five.

**`features/reauthenticate` owns the dialog**: Google, and a TOTP step when the policy asks for one.
It registers itself as the handler at the application root. Nothing else knows it exists.

**Features call the API as usual.** What the person typed survives because the page never leaves.

Repeating the request is safe even for `POST`: the 401 is decided before the request reaches any
business logic, so the refused request had no effect.

**Google opens in a browser popup**, and the result returns to the original tab through
`BroadcastChannel`, because navigating to Google would lose the page. If the browser blocks the
popup, the dialog shows an "Open Google sign-in" button: a popup opened by a click is not blocked.

**The return address after an ordinary sign-in** is accepted only as a relative path inside `app.`,
checked by the existing `isSafeRelativePath` from `frontend-shared/lib`. Otherwise
`app.<domain>/login?return=https://evil.com` would turn our sign-in into a phishing tool (open
redirect).

## Alternatives considered

**Redirect to `/login` with a return address.** Simpler, and rejected: unsaved input is lost, which
is the one thing an expired session must not cost the person.

**Each feature handles 401.** Rejected: every feature would carry the same logic, and the first one
that forgets it loses data.

**Refreshing the session silently in the background.** There is nothing to refresh with: sessions
are server-side and an expired one needs the person (ADR-079).
