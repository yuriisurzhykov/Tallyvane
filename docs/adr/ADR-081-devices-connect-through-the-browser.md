# ADR-081. The extension and the mobile client connect through the browser with PKCE

## Status

Accepted.

## Context

In the browser a session appears naturally: the person signs in on `app.` and the cookie is set.
The extension and the future mobile client have no sign-in screen of their own. Repeating Google,
TOTP and the policies in every client would duplicate exactly what ADR-078 centralised.

Neither client can keep a client secret: its code is in the user's hands.

## Decision

**The client opens `app.<domain>/connect` in the system browser**, with a fresh random
`code_challenge`. There the person is already signed in, or goes through the ordinary sign-in
policy, and sees a consent screen that names the client and lists in plain words what it will be
able to do. Allowing it returns a short-lived, single-use code to the client. The client exchanges
the code together with its secret `code_verifier` for a token on `api.<domain>`.

This is OAuth 2.0 Authorization Code with PKCE (RFC 7636) for native apps (RFC 8252), with us as a
minimal authorization server. In Chrome it is `chrome.identity.launchWebAuthFlow`; on iOS,
`ASWebAuthenticationSession`. PKCE proves that the instance exchanging the code is the one that
started the flow, not someone who saw the redirect. All sign-in logic stays in one place, on `app.`.

**The token is the same kind as the browser's** (ADR-079): opaque, hashed in the database, listed
among devices, revoked instantly. It adds two properties:

- **client type**: `browser`, `extension`, `mobile`. The policy sets lifetimes per type, within
  the bounds in code: an extension with a narrow scope may reasonably live longer than a browser
  session;
- **scope**: what the token may do. The extension's token may only capture jobs, as §11.2 already
  says.

**The calendar feed is a separate kind of credential, not a session.** Calendar applications send
no headers and cannot sign in, so the only option is a secret in the URL: unguessable, read-only,
with no names of people and no notes in the feed. It is stored hashed, the user can regenerate the
link (the old one stops working at once), and it never appears in `amr`, is never extended and can
do nothing but read the feed.

**Webhooks are out of scope.** They authenticate systems, not people, with a signature and a
timestamp, as §11.2 already says.

## Alternatives considered

**A pairing code typed into the extension.** Simple, but clumsy, and easier to phish ("read me the
code").

**The extension reads the `app.` session cookie.** Technically possible with the `cookies`
permission, and rejected: its access would be the full browser session with no scope, and it would
die with that session.

**The browser's lifetimes for every client.** Rejected: the extension would have to be reconnected
every week.
