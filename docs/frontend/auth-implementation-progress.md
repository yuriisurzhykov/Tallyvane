# Authentication implementation progress

Status: **in progress; not ready for production deployment**. This checklist describes verified
local behavior and the remaining work so the deployment guide is not mistaken for a release signal.

## Implemented and verified

- Password registration and sign-in, email verification, email-code sign-in, password recovery,
  password change, TOTP and email MFA enrollment/verification, one-use backup codes, session list,
  revocation, refresh, logout, CSRF double-submit and origin checks.
- Email challenges are purpose-bound, hashed, single-use, attempt-limited, expire after ten minutes,
  and enforce a sixty-second resend delay. PostgreSQL tests cover concurrent issuance/consumption
  and SMTP recovery behavior.
- Google Authorization Code + PKCE sign-in validates state, verifies the returned identity and
  refuses implicit linking by email. Explicit Google linking now rechecks the connected password,
  binds the callback to the same account and session, and atomically claims a provider subject;
  unlinking requires fresh action proof and refuses to remove Google's credential when that would
  leave no policy-enabled sign-in path. The app's account-security view exposes these actions when
  Google is configured.
- Authentication policy is versioned in PostgreSQL and is the server's sole sign-in authority.
  Public sign-in options expose only enabled, runtime-available primary methods. After a valid
  primary proof, the server selects the highest-assurance satisfiable scheme, recommends its factor,
  and permits every factor from another satisfiable enabled scheme. A primary-only scheme issues a
  session when no factor-bearing scheme is satisfiable; enrollment is never forced. Pending sign-ins
  store the policy version and expire if that version changes.
- The admin policy API verifies a confirmed, enabled user against `TALLYVANE_ADMIN_EMAILS` on the
  server. Policy edits and denied/conflicting writes are audited. Admin MFA reset removes TOTP,
  email and backup factors, revokes sessions and refresh tokens, deletes pending sign-ins, and
  records the reset in the same administrative journal.
- Account security now supports normal-user second-factor removal only after a password or linked
  fresh action proof. Removal requires explicit confirmation and rejects stale verification. TOTP
  enrollment also requires recent verification; policy-driven sign-in does not force enrollment.
  The account page offers password/Google recheck, TOTP QR/manual setup, copying the setup key, and
  a confirmation drawer for removal.
- App authentication surfaces use the shared design-system Button, Field, Input and ToastRegion.
  The page is composed in `views/authentication`, its provider/product panel and authentication
  step are widgets, and visible copy comes from i18n. The email registration form is a keyboard-
  operable expandable section. Field errors are localized and focused; global errors and meaningful
  successes are announced through the bottom-right toast region.
- The local compose file has a separate PostgreSQL volume, SMTP-only Mailpit with persistent
  storage, loopback-only published ports, host-routed localhost subdomains, and no external email
  relay. Local auth cookies remain host-only, so app and admin sessions are independent. Local data
  does not share the production database.
- Identity application/web/server unit tests and backend `ktlintCheck` pass. The 58-test identity
  infrastructure integration suite passes in Docker, including Argon2 and PostgreSQL; its fixture
  now uses a unique database name per run so retained test Postgres state does not collide. The
  workspace TypeScript check and frontend ESLint pass; all three Next.js apps build, and the 13-test
  auth browser suite passes. App and web FSD/dependency-graph checks pass when run individually.
  The recursive `pnpm run arch` and admin token architecture entry point hit Node's
  `uv_os_get_passwd returned ENOMEM` on this host; they are not reported as passing. The Argon2
  integration suite remains container-only because the Windows Gradle JVM cannot load `argon2.dll`.

## Still required before deployment

1. Finish the full test matrix from a clean local database, including end-to-end OAuth link/cancel,
   factor-disable and admin-reset scenarios, policy-driven sign-in and concurrency, plus browser
   accessibility checks after the final component move.
2. Run a manual, production-like local smoke test through the real app and admin UI: register a
   fresh account, verify it through Mailpit, exercise password/email-code/recovery, enroll and use
   MFA, consume backup codes, edit policies, and verify admin denial/reset behavior. This uses the
   existing isolated localhost stack and real flows; seeded accounts, scenario-control APIs, and
   a separate demo UI are not required.
3. Configure a Google OAuth client for localhost and run live sign-in/link/cancel checks. Production
   Google, DNS, Cloudflare Access, backup and rollback actions require the owner's accounts and are
   intentionally left to the operator instructions.
4. Add an independent sign-in entry point to `frontend-admin`. Its policy UI calls protected admin
   endpoints and correctly requires an identity session, but the admin app currently has no login
   route; it cannot establish its own host-only session yet. Do not solve this by sharing auth cookies
   between the app and admin hosts.

## External deployment boundary

The owner will configure Google Cloud OAuth, Cloudflare Access and DNS. No production credentials
are stored in this repository and no remote deployment or database migration has been performed.
Keep `docs/operations/auth-deployment.md` gated until every item above is verified and the external
configuration is complete.
