# ADR-083. A security journal the user can read, and notifications behind a port

## Status

Accepted.

## Context

The fastest way to notice that an account was taken over is for its owner to see what happened to
it: a sign-in from a new device, TOTP disabled, a recovery code used. The system sends no email yet,
and the owner does not want that absence to shape the design.

## Decision

**A security journal, visible to the user** on the "Activity" part of the security settings:
sign-ins, new devices, TOTP enabled and disabled, recovery codes used and regenerated, "sign out
everywhere", calendar link regenerated. Each entry has the time, the event and the device. The table
is append-only.

It is its own small module (ADR-076), which listens to events published by
`identity`, `authentication` and `sessions` and reads nothing synchronously. Policy changes in the
admin are journalled by `authentication` itself, with author and comment (ADR-078).

**Notifications go through a `SecurityNotifier` port.** Its first adapter writes the event to the
log. An email adapter replaces it later without touching the logic that decides what to notify.

**No secret reaches a log, ever**: no tokens, no codes, no TOTP secrets. Only the event type, the
account, the device and the time.

## Alternatives considered

**A journal for the administrator only.** Rejected: the person best placed to recognise "this was
not me" is the account's owner.

**No journal.** Rejected by the owner.

**Email notifications in the first slice.** Deferred: the journal already holds the data, and email
delivery is a separate piece of infrastructure; the port makes adding it a new adapter, not a
redesign.
