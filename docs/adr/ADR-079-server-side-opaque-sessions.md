# ADR-079. Sessions are server-side records behind an opaque token

## Status

Accepted. Replaces the access and refresh token pair of the `feature/authentication` attempt.

## Context

Once the policy is satisfied (ADR-078), the person needs something that saves them from signing in
on every request. The requirements it must meet:

- it works the same way for the browser, the extension and the future mobile client;
- it can be revoked instantly, the user sees the list of devices, and it has a hard maximum age;
- it remembers which factors were verified and when, so "a dangerous action needs a fresh factor"
  is a comparison rather than a separate mechanism.

## Decision

**A random 256-bit token, opaque to the client; the database stores only its hash.** The hash is an
HMAC with a server-side key (the "pepper") that lives outside the database and carries a version
number, recorded on each session. A leaked database or backup holds no usable sessions, and the key
can be rotated without signing everyone out: new sessions use the new version, old ones are checked
with the version they were made with until they expire.

**The session record holds** the account, the client type and device description, the scope, `amr`
(the factors verified, RFC 8176), the time of authentication and the time of last activity.

**Lifetimes are computed on every request from the active policy**, not stored as an expiry: an
idle lifetime (time since last activity) and an absolute lifetime (time since authentication),
separately per client type. Initial browser values: one day idle, seven days absolute. Tightening
the policy therefore applies to existing sessions at their next request (ADR-078).

**A new token whenever trust changes.** After sign-in and after a second factor, the old token
stops working and a new one is issued. An attacker who planted a known token before sign-in
(session fixation) holds nothing afterwards.

**Fresh factor for dangerous actions: 5 minutes**, from the policy. Disabling TOTP, regenerating
recovery codes, signing out other devices, changing a policy: each checks the session's
authentication time against the policy and, if it is older, asks for a step-up (ADR-078).

**Revocation is deleting the record.** The next request with that token gets 401. "Sign out
everywhere" revokes every session and device token of the account except the current one.

**Transport differs, the core does not.** The browser carries the token in a cookie (ADR-080); the
extension and the mobile client send the same kind of token in `Authorization: Bearer` (ADR-081).

The cost is one indexed lookup per request, a fraction of a millisecond at this scale.

## Alternatives considered

**A short-lived JWT with a refresh token.** Its one advantage, checking a request without the
database, matters when many services share no session store. Here there is one process and one
database. Its costs all hit the requirements: revocation is not instant, signing keys need
managing, every client needs refresh logic with its races, and the refresh token is stored in the
database anyway.

**A long-lived JWT without refresh.** Cannot be revoked. Rejected outright.

**Opaque access plus opaque refresh, both checked in the database.** What the previous attempt
built: the complexity of the JWT design without its one advantage.

**Storing an expiry on the session.** Simpler to query, rejected because a tightened policy would
then only apply to sessions created after the change.
