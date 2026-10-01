# ADR-077. Google is the only way to register, and the system keeps no passwords

## Status

Accepted.

## Context

Registration is open to everyone, and the product spends paid resources (LLM calls) on behalf of
its users. The threats the owner ranked first are, in order: bots creating accounts, a stolen
device, a compromised account. The data at stake includes direct personal data (phones, emails,
photos), indirect data (workplaces, contacts, dates) and personal data of third parties
(recruiters).

There are two ways to learn who someone is. Either the system stores a secret the person chose (a
password) and compares it on every sign-in, or it asks someone the person already proved
themselves to: Google, Apple, or their own device (a passkey).

## Decision

**Registration and sign-in are separate processes**, each with its own policy (ADR-078).

**Google (OpenID Connect, Authorization Code with PKCE) is the only way to create an account.**
The account is keyed by Google's `sub` claim, never by the email address: an email can be
recycled or changed at Google, a subject cannot. Google must report the email as verified, or
registration is refused with an explanation.

**No passwords of our own, and no sign-in by an emailed code.** Nothing secret about a person's
sign-in is stored on our side, so nothing can be stolen from our database or guessed on our site.

**Planned, not built:** passkey as an additional factor for an existing account; Apple together
with the mobile client, since the App Store requires it when Google sign-in is offered.

**Linking a second provider to an existing account is not decided here.** With one provider there
is nothing to link. When Apple arrives, the rule has to be chosen then; the constraint fixed now is
that accounts are never merged automatically because two providers report the same email, which
is the classic account-takeover hole.

## Consequences

A person without a Google account cannot register. For a job-search tool this is accepted as the
price of pushing the cost of creating an account onto Google (phone verification, abuse
detection), which is the strongest available defence against the first-ranked threat.

A compromised Google account is a compromised account here, unless the person enabled a second
factor (ADR-078, ADR-082).

## Alternatives considered

**Own passwords (Argon2id).** Rejected: brute force and credential stuffing, a reset flow that
would need email anyway, and responsibility for leaked hashes. The previous attempt implemented
and then removed them.

**Code or magic link by email.** Rejected: disposable mailboxes give bots unlimited accounts, and
the mailbox becomes the only key.

**Passkey as the way to register.** Rejected for now: it needs a recovery path for a lost device,
and on its own it does nothing against account farms.
