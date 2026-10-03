# ADR-093. TOTP is an enrolment with a standing, and a recovery code retires it

## Status

Accepted. Chosen by the owner on 2026-10-03 while planning slice 5 (`https://claude.ai/artifact/MPRF3cvNdJJ2hmRWNpQwmr`,
and the 5b plan `https://claude.ai/artifact/1YuuAe8eLNtZ2scVy6khox`; design and diagrams in
`docs/backend/03-authentication-slice-5b-totp.md`), each time the recommended option: spending a recovery code retires
the TOTP seed (fork 3); wrong codes are counted per account in a table of the `authentication` database (4).
Forks 1 and 2 concern the step-up and are recorded in ADR-092.

Refines ADR-079 (the second factors and what they protect) and ADR-082 (how a second factor is operated: pauses,
limits, recovery codes, no lockout).

## Context

ADR-079 names TOTP and recovery codes as the second factors, and ADR-082 describes how they should behave: a growing
pause after a wrong code, a limit per attempt, a limit that outlives the attempt, and no account lockout. Slice 5a made
dangerous acts ask for a fresh proof. What was missing is the factor itself: how a person turns it on and off, what a
recovery code does to it, where a seed may be kept, and where the account's own count of wrong guesses lives, given that
an attempt is short-lived and an attacker can open as many of them as they like.

## Decision

**TOTP is an enrolment with a standing.** An account has at most one `TotpEnrollment`, which is `Pending` (begun, no
code typed yet, counts for nothing), `Active` or `Retired` (the seed no longer works, the recovery codes do). Beginning
while `Active` is a conflict; beginning while `Pending` or `Retired` starts over with a new seed. Confirming makes the
enrolment `Active` and issues a whole new set of recovery codes, replacing any older one. Disabling removes the enrolment
and, with it, the codes.

**The parameters are the ones every authenticator app speaks.** HMAC-SHA1, six digits, thirty-second steps; the
current step and one on each side are accepted, to forgive a slow hand and a clock that is a little off. The three
candidate codes are compared in constant time. **A step is accepted once:** the enrolment remembers the step of the last
code it took, and afterwards only strictly later steps are accepted, so a code that was shoulder-surfed or phished cannot
be replayed within its thirty seconds. Two requests that carry the same code are told apart by reading the enrolment
`for update`; the second one waits for the first to commit and then finds the step spent.

**The seed is sealed at rest.** Twenty random bytes, shown as base32, are encrypted with Tink AES-256-GCM under a
keyset the deployment holds in `TALLYVANE_TOTP_KEYSET`, so a copy of the database is not a copy of anyone's
authenticator. Tink's ciphertext carries the id of the key that sealed it, so a keyset with a newer primary key still
opens older rows. The domain object holds the seed as a `Secret` and tells it to the store through a record (ADR-085);
the store seals it on the way down and opens it on the way up. No domain class meets a cipher. The server refuses to
start without a keyset.

**Recovery codes are ten keyed digests, each valid once.** Ten characters from a 32-letter alphabet without look-alikes
(shown as `ABCDE-FGHJK`), fifty bits each, kept as HMAC digests under the same pepper as attempts and sessions. Typing
ignores case, dashes and spaces. Fifty bits cannot be guessed at the speed this API allows, so a wrong recovery code
counts against the attempt only, not against the account.

**Fork 3. Any accepted recovery code retires the seed.** The person who types one has, by the way of it, lost their
phone, or at least cannot be sure of it; a phone in a stranger's hand should stop being a second factor at once. So the
spent code is recorded and the enrolment moves to `Retired` in the same transaction, at sign-in and at a confirmation
alike. Only the remaining recovery codes work from then on, until the person turns TOTP on again, which creates a new
seed and ten new codes.

**What counts as enrolled.** TOTP is enrolled while the standing is `Active`; a recovery code is enrolled while one is
unspent. The second step applies to a person with either. When a `Retired` person spends their last code nothing is
enrolled any more and Google alone signs them in, the same as for a person who disabled TOTP. The alternative, a second
step nobody can pass, is the account lockout ADR-082 refuses. `Enrollment.of(totp, codes)` is the single place that
says so.

**Fork 4. Wrong TOTP codes are counted per account, in a table.** `second_factor_failures` holds one row per wrong
code, whichever attempt it came in. The pause is computed from the failures of the last fifteen minutes: the policy's
first delay, doubled for every further failure, at most five minutes. While a pause runs, the code is not even checked.
The attempt keeps its own count, which closes it after five wrong answers (ADR-082), so the account's count is what
stops an attacker who opens a fresh attempt for every five tries. Rows older than the window are removed as new ones
are written. There is no lockout: the person who owns the account can always sign in, at worst after a pause.

**A refusal that commits.** A wrong code is recorded in the attempt and in the account's count in a transaction that
commits although the answer is a refusal, otherwise a client that makes every request fail would never be counted. The
exceptions are the requests that did not cause the failure: one that lost a race to change the attempt answers `409`
and, if its code was right, rolls back so the code is not spent for nothing; if its code was wrong, the account's count
is kept all the same, so racing requests do not buy guesses.

**The edge.** Beginning, disabling and reissuing declare `Access.SignedFresh` (ADR-092). Showing the standing and
confirming the first code need only `Access.Signed`: the first code is itself the proof. The answers that carry a key or
codes are withheld from idempotent replay (`SecretAnswer`), so a stored response never holds a secret. The sign-in
state, `GET /sign-in`, and the code answer, `POST /second-factor-codes`, are `Public` like the sign-in they continue:
the attempt cookie is what they check. `platform:http` gains two meanings, `gone` (`410`, the attempt is over) and
`slowDown` (`429`); the pause travels in `Retry-After`.

## Alternatives considered

**Keep the failure count in the attempt only.** Then a fresh attempt starts with a clean slate and the limit is a speed
bump. An attacker needs a Google sign-in for each attempt, but a person who has the Google password has it for as many as
they like. The count has to outlive the attempt.

**Keep the failure count in memory or a cache.** A restart, or a second instance, forgets it. A table in the database
that already holds the enrolment gives the same answer from every instance and needs no new infrastructure.

**Lock the account after N wrong codes.** Turns a stolen password into a way to lock out its owner; refused by ADR-082.

**A recovery code leaves the seed alone.** Simpler, and wrong for exactly the person who needs the code: they lost the
phone, and the phone still works as a factor for whoever has it.

**Store the seed in the clear, or hash it.** A hash cannot produce a code. Clear text makes every database copy, backup
and replica a copy of every authenticator.

**Encrypt with a key derived from the pepper.** One secret that has to be rotated for its own reasons would then also
decide whether old seeds can be read. A separate keyset rotates on its own.

## Consequences

A person who turns TOTP on gets two steps in the API (`POST /totp-enrollments`, `POST /totp-confirmations`) and ten
codes shown once. A sign-in with TOTP on waits, after Google, for `totp` or `recovery_code`, and so does a confirmation of
a dangerous act; `sessions` does not change, since it already asks `authentication` what a sign-in lacks. The migration
adds `totp_enrollments`, `recovery_codes` (cascade-deleted with the enrolment, one digest once per account) and
`second_factor_failures`.

Deployments need `TALLYVANE_TOTP_KEYSET` (a Tink keyset in JSON form; `TOTP_KEYSET` in `ops/.env`) before they start;
`ops/README.md` says how to generate one. Losing the keyset makes every sealed seed unreadable, so it is kept with the same care as the pepper, and
the recovery codes (which do not depend on it) are what lets people back in.

Recovery codes are digests under the token pepper, and the pepper port knows one pepper only, like sessions and attempts
do today. Until retained older peppers can be read by version (ADR-079), rotating `TOKEN_PEPPER` makes every unspent
recovery code fail, so a rotation has to wait for that.

The console screens come with slice 5c. Until then TOTP can be driven through the API only.
