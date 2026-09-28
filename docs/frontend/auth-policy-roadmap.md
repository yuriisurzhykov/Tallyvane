# Authentication policy roadmap

## Approved first release

Real Kotlin identity backend, PostgreSQL persistence, Google OAuth, password and
email-code sign-in. Email verification, recovery, Authenticator and one-use
backup codes share purpose-bound authentication challenges. Policies select
ready-made schemes by primary sign-in method and enrolled factors. Required
enrollment cannot grant application access until it completes.

The admin editor supports safe defaults and an explicitly acknowledged advanced
mode for non-independent checks. Only verified, allowlisted administrators may
change policy or reset MFA; both actions are audited. A policy version is pinned
to each authentication attempt.

English UI, both existing themes, spacious forms beside a product panel. Local
demo mode uses isolated data and controllable external adapters.

## Deferred proposals

- User groups: select policy by group membership, including separate requirements
  for administrators. Requires explicit group ownership and precedence rules.
- New device: require additional verification when a device is not recognized.
  Requires a defined device-trust lifecycle and revocation behavior.
- Verification age: require a fresh check after a configured interval. Requires
  a policy for sensitive operations and a server-owned verification timestamp.

These proposals were discussed with the owner and deliberately deferred. They
must not silently become conditions in the first-release policy editor.

## Deployment decisions

Use app.surzhykov.icu, admin.surzhykov.icu and mail.surzhykov.icu behind
Cloudflare Access restricted to yuriisurzhykov@gmail.com. Keep surzhykov.icu
public. Admin is already protected. Mailpit receives SMTP without forwarding
messages externally; never print codes in general application logs.

Email OTP defaults: six digits, ten minutes, sixty-second resend interval,
five attempts. Issue ten backup codes. Passwords accept 15–128 characters
without mandatory character classes.
