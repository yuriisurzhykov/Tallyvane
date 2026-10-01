# ADR-082. TOTP codes are single-use, failures slow the attempt down, recovery codes are mandatory

## Status

Accepted.

## Context

TOTP (RFC 6238) is the second factor a user enables themselves (ADR-078). A six-digit code is a
million possibilities, so without limits it can be guessed. A lost phone without a recovery path
loses the account forever, and a careless recovery path becomes a way around the second factor.

TOTP is checked after Google, so only someone who already controls the victim's Google account can
guess codes. That is exactly the case TOTP exists for, so the defence against guessing is not
optional.

## Decision

**Each code is accepted once.** The server remembers the last accepted time step per factor and
refuses the same code again.

**Failures slow the attempt down exponentially.** After each wrong code the next try is possible
after 1, 2, 4, 8… seconds, and the attempt dies after 5 failures; the person starts the sign-in
again. Guessing becomes pointlessly slow, while a person who mistyped twice barely notices. Failures
are counted per attempt and per account, in the application, on top of nginx's per-IP rate limit
(`limit_req zone=api`).

**Recovery codes are mandatory.** Ten codes are issued when TOTP is enabled, each single-use,
stored only as hashes. A recovery code is itself a factor (`recovery_code`), so the policy, not the
code, decides what it is worth. After signing in with one, the person must set up the second factor
again, and the remaining count is shown.

**Two keys live outside the database, each with a version**: the HMAC key for hashing tokens
(ADR-079) and the key that encrypts TOTP secrets (Tink). Each record stores the version it was made
with, so either key can be rotated without invalidating everything at once.

**Disabling TOTP and using a recovery code are dangerous actions** and require a fresh factor
(ADR-079).

## Alternatives considered

**Locking the account for N minutes after K failures.** Easier to explain, and rejected: it hands an
attacker a way to lock the owner out by failing on purpose.

**Optional recovery codes.** Rejected by the owner: a lost phone would mean a lost account.

**Recovery through email.** Rejected: it would make the mailbox a way around the second factor, and
the system sends no email yet.
