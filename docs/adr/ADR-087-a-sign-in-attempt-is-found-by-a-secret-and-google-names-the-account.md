# ADR-087. A sign-in attempt is found by a secret the browser holds, and Google is what names the account

## Status

Accepted. Chosen by the owner on 2026-10-02, fork by fork, while planning the first Google sign-in
(`/mnt/project-files/auth-design/slice-3/01-forks.md`): three pull requests (fork 1 B), an attempt secret
with a stored digest (2 A), a registration begun when Google names someone unknown (3 A), the account
named inside the attempt (4 A), a clock that pulls late stamps forward (5 B).

## Context

ADR-078 made the sign-in attempt a stored aggregate and kept its rows by a UUIDv7 id. That id is the key
the browser would have to carry between the page that starts a sign-in and the page Google sends the
person back to, and a UUIDv7 is the wrong thing to carry: it publishes the moment it was made, spends at
most 74 bits on randomness, and ADR-052's `IdGenerator` says outright that such an id is not a secret. Whoever
can guess or read one can continue somebody else's sign-in.

The first real sign-in also raised three questions the policy model had left open. Who says whose account
an attempt is, before any account is known. What happens when the person Google names has no account. And
what an attempt does when two servers' clocks disagree by a few milliseconds.

## Decision

**The browser holds a random secret, and the database holds its keyed digest.** The `__Host-attempt`
cookie carries 256 bits from a CSPRNG (`SecretGenerator`); attempts are found by
`HMAC-SHA256(pepper, secret)` (`Digests`), with the pepper's version kept beside each digest so a pepper can
be rotated. The UUIDv7 stays as the row's internal primary key and is never sent to anyone. Reading the table
does not let anyone continue a sign-in, and nothing about one can be guessed. The cookie is `HttpOnly`,
`Secure`, `Path=/`, no `Domain`, `SameSite=Lax` (`Strict` would drop it on the redirect back from Google).

**Google's three secrets are kept in the database, single-use.** `state` (proves the redirect answers a
sign-in this browser began), `nonce` (proves the ID token was made for this trip) and the PKCE verifier
live in `authentication.google_handshakes`, one row per attempt, deleted when the reply is taken. The handshake is taken
and the transaction committed *before* Google is asked, so a reply that arrives twice finds nothing the second
time, and no transaction is held open over Google's network.

**Every sign-in starts as a login.** Nobody can tell a new person from a returning one before Google has
spoken. When Google names a subject with no account, the attempt does not change purpose: a new `registration`
attempt is begun at that moment, already holding the same Google factor, under a *new* secret and cookie, and
the login attempt is forgotten. What Google said about the person (name, verified address) waits beside the
registration in `authentication.google_profiles` until they finish the welcome form, and goes with it.

**The factor that identifies the account carries its subject.** `VerifiedFactor.identifying(Google, sub, at)` and
`VerifiedFactor.confirming(Totp, at)` are the only two ways to make one, and each refuses to be the other.
An attempt refuses a second subject ("already belongs to one person"), so a second Google account half-way
through cannot be a step of the first sign-in. `Progress.Complete` and `Restricted` carry the subject, which
is how `sessions` will learn whose session to issue without `authentication` ever naming a user.

**Late stamps are pulled forward, not refused.** `withVerified` and `withFailure` record a stamp earlier than
the attempt's start or its last entry at that later moment: a person is not turned away because one server's
clock is milliseconds behind another's. `restore` stays strict: a stored history out of order was not written
by this class.

**The callback does not issue a session.** It records the Google factor and redirects to a fixed page of the
application. Redeeming a complete attempt is `sessions`' step (ADR-076: authentication never grants access).
The return path is not stored on the server: the frontend keeps it and validates it as a relative path, and the
backend only ever redirects to pages it names itself, so the callback cannot be made an open redirect.

## Alternatives considered

**A UUIDv7 in the cookie.** Guessable in principle and readable for what it is; ADR-052 already says no.
**A signed cookie holding the whole attempt.** Nothing to store, and nothing to forget: a stolen cookie
replays until it expires, the failure counter lives in the client, and a signing-key rotation drops every
sign-in in flight. **Starting as a registration** when the email looks new: wrong, an address says nothing
about the subject. **Moving one attempt from login to registration** by mutating its purpose: an attempt only
grows, and a purpose that can change is a policy that can be switched. **Refusing late stamps:** the failure
the owner would see is a spurious "try again" at the one moment he is least able to say why.

## Consequences

`authentication.attempts` gained `secret_digest` and `pepper_version`; attempts that existed were deleted by
the migration, being minutes old by design. `attempt_verified_factors` gained `subject`, constrained to
exist exactly for Google.

The deploy supplies `TALLYVANE_TOKEN_PEPPER` (at least 32 characters) and its version, the Google client id
and secret, and the origins of the application and the API. The redirect URI registered with Google is
`<api origin>/api/v1/google-return`.

Changing the pepper without bumping its version strands in-flight attempts, which is acceptable; rotating
means running two versions side by side, which the stored version allows and no code does yet.

Database rows hold Google's `state`, `nonce` and verifier in clear for the minutes a trip lasts. They are
single-use and worthless after the reply, and hashing them would not help: the adapter must send the verifier
to Google as it is.
