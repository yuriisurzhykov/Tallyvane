# ADR-094. The code screens ask the server where the attempt stands

## Status

Accepted. Chosen by the owner on 2026-10-03 while planning slice 5c
(`https://claude.ai/artifact/WGszxZE9gLPqHENAtdWzNd`; diagrams in
`docs/backend/04-authentication-slice-5c-totp-screens.md`), each time the recommended option: the key is shown with a
QR code drawn in the browser (fork 1); a sign-in by recovery code is followed by a notice before the console (2).

Refines ADR-089 (the console talks to the API through a chain of one-job transports) and ADR-092 (a dangerous act asks
for a fresh proof).

## Context

Slice 5b gave the server a second step of a sign-in and a confirmation, and seven routes to run it. Google always
returns the browser to `/login/continue` or `/step-up/continue`; the server does not pick a page for the code. The pages
that exist send `POST /sessions` or `POST /step-ups` at once, which with TOTP on would meet an attempt that still waits.
The console also has no place to turn TOTP on and off.

## Decision

**The state is read, never remembered.** `GET /sign-in` says where the attempt stands. `/login/continue` becomes the
dispatcher that reads it: a complete attempt is redeemed, a waiting one goes on to `/login/verify`, anything else says
"start again". `/login/verify` only asks for a code and goes back to the dispatcher, which reads the state again. The
confirmation window does the same and ends with `POST /step-ups`. A refresh, the back button and a second tab therefore
cannot leave a page believing something the server has moved past.

**One form, two places.** The code form is a feature (`answer-second-factor`), and the two views that need it put it
together with `open-session` or `reauthenticate`; features do not import each other.

**Objects at the border.** `SignIns` and `SecondFactors` turn the server's answers into objects (`SignInState`,
`CodeAnswer`, `Standing`, `TotpKey`, `RecoveryCodes`) whose fields are private. A page asks the object what to do
(`proceed`, `when`) and does not take an answer apart, so a status code is read in one place.

**A pause is a countdown, and a code is never sent by itself.** `Retry-After` is read in seconds and counted down on
screen; the field waits. The sixth digit does not submit, because every wrong code lengthens the pause.

**Fork 1. A QR code drawn in the browser.** The `qrcode` library, behind a `QrCode` primitive in `frontend-shared`, so
nothing else knows it; the key never leaves the browser. The key to type and the `otpauth://` link are shown beside it.

**Fork 2. A notice after a recovery-code sign-in.** Spending a recovery code retires the TOTP seed (ADR-093), so the
authenticator stops working. The person is told how many codes are left and offered "Set up now" or "Continue". The
Security card shows the `retired` standing either way.

**Turning off and reissuing ask once, in place** (as in ADR-091), in front of the confirmation dialog.

**Cancelling the confirmation dialog is not a failure.** `StepUpDeclined` lets a caller tell "the person said no" from
"it broke".

**Secrets stay in memory.** The key, the link, the QR code and the recovery codes are held by the component that shows
them and nowhere else.

## Consequences

The sign-in costs one more request (`GET /sign-in`) than before, always. `qrcode` is a new dependency of
`frontend-shared`. A person who leaves the page before saving the recovery codes must reissue them, which asks for a
fresh proof. Not verified against the real backend or Google: only a stand-in API in Chromium.
