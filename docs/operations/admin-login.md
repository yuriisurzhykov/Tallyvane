# Administrator sign-in

This guide covers the administrator login flow and the identity records it uses. The admin site
and the main application have separate authentication realms, even when both accounts use the same
email address.

## Where administrators sign in

Open the admin hostname and use its `/login` page. The admin frontend sends authentication requests
to `/api/v1/auth/admin/*`; the main application continues to use `/api/v1/auth/*`. An admin session
is created only by the admin endpoints. Signing in to the main application does not authenticate
the admin site.

After successful sign-in, the admin site allows return paths within `/pages`, `/media`, `/strings`,
and `/authentication`. Invalid or main-app return paths fall back to `/pages`.

## How an admin account is provisioned

`TALLYVANE_ADMIN_EMAILS` is the server-side comma-separated allowlist. An address must be present
there to sign in as an administrator.

On the first admin login page load, the server handles
`GET /api/v1/auth/admin/sign-in-options`. During that request it copies each configured address
that already has an active, verified user account into the separate administrator tables. The
copy receives a new admin ID and an initial snapshot of the password hash, Google credential, TOTP
enrollment, and email-factor enrollment. Existing user sessions are not copied. After provisioning,
changes to either account's password, Google link, or MFA state do not synchronize to the other
account.

For password or email-code sign-in, first create and verify the address through the normal app; then
open the admin login page to trigger provisioning. Email-code sign-in does not create an admin
account. If Google OAuth is configured for the admin site, a successful Google sign-in may create
an allowlisted admin account directly, using Google's verified email address.

Provisioning is idempotent: an existing admin record is not overwritten from the user record. If an
admin account already exists, update or recover its admin credentials through the admin realm; do
not expect later user-account changes to carry over. Removing an address from
`TALLYVANE_ADMIN_EMAILS` blocks new sign-ins and the admin policy authorization check, but does not
delete its admin row or revoke existing session rows. Revoke the admin's sessions before removing
the address when immediate session invalidation is required.

## Separate data and sessions

The two realms have independent records in PostgreSQL:

| Data | User realm | Admin realm |
| --- | --- | --- |
| Accounts | `identity.users` | `identity.admins` |
| Password credentials | `identity.password_credentials` | `identity.admin_password_credentials` |
| Google credentials | `identity.google_credentials` | `identity.admin_google_credentials` |
| MFA enrollments | `identity.totp_enrollments`, `identity.email_mfa_enrollments` | `identity.admin_totp_enrollments`, `identity.admin_email_mfa_enrollments` |
| Pending MFA sign-ins | `identity.pending_authentications` | `identity.admin_pending_authentications` |
| Email challenges | `identity.email_challenges` | `identity.admin_email_challenges` |
| Recovery-code storage | `identity.backup_codes` | `identity.admin_backup_codes` |
| Sessions and refresh tokens | `identity.sessions`, `identity.refresh_tokens` | `identity.admin_sessions`, `identity.admin_refresh_tokens` |
| Action proofs and audit events | user-owned tables | admin-owned tables |

There is no foreign key connecting an admin row to a user row. Matching email addresses do not
make the two identities interchangeable. The admin access token is resolved only against admin
sessions; admin MFA challenges and refresh tokens are also stored in admin tables.

The browser access cookie is host-only. In the normal deployment, the admin and app use separate
hostnames, so their cookies are separate as well. The admin refresh cookie is sent only to
`/api/v1/auth/admin/refresh`; the app refresh cookie uses `/api/v1/auth/refresh`. Sign out of the
admin site to revoke the current admin session. Use the admin `logout-all` endpoint to revoke all
sessions for that admin account.

## Available login methods

The server's persisted sign-in policy controls which methods the admin page offers. The login page
reads the choices from `GET /api/v1/auth/admin/sign-in-options`; the browser does not decide policy
or bypass unavailable server methods.

- Password: `POST /api/v1/auth/admin/login/password`.
- Email sign-in code: request a code with `POST /api/v1/auth/admin/login/email/code`, then submit it
  to `POST /api/v1/auth/admin/login/email/verify`. This requires a provisioned admin account.
- Google OAuth: start at `GET /api/v1/auth/admin/google/oauth/start`; Google returns to the admin
  callback configured below.
- Second factor: the server can require TOTP or an email code. The admin page uses
  `POST /api/v1/auth/admin/mfa/verify`; for email MFA it first requests a challenge through
  `POST /api/v1/auth/admin/mfa/email/request`.

Password recovery must also use the admin realm. The server exposes
`POST /api/v1/auth/admin/password/forgot` and `POST /api/v1/auth/admin/password/reset`; resetting a
password through the main app changes only the user account. The current admin login page does not
show a password-reset form. For direct API calls, fetch a CSRF token from
`GET /api/v1/auth/csrf` and send it in `X-CSRF-Token` with each POST. The admin frontend client does
this automatically for its supported requests.

The admin sign-in screen accepts TOTP and email MFA codes. Recovery codes are stored separately and
are not a second-factor option on the admin sign-in screen.

If Google is not configured for the admin callback, the server omits Google from the methods it
returns to the admin login page, even when Google sign-in is enabled for the main application.

## Optional Google OAuth setup

Configure the shared OAuth client ID and secret and the main-app callback as usual:

- `TALLYVANE_GOOGLE_CLIENT_ID`
- `TALLYVANE_GOOGLE_CLIENT_SECRET`
- `TALLYVANE_GOOGLE_REDIRECT_URI`

To enable Google on the admin login page, also set
`TALLYVANE_GOOGLE_ADMIN_REDIRECT_URI` to the exact registered Google callback URI. For example:

```text
https://admin.example.com/api/v1/auth/admin/google/callback
```

Register the app and admin callback URIs separately in the Google OAuth client. The callback path
is distinct because the admin OAuth state cookie is scoped to `/api/v1/auth/admin/google`. Keep the
client secret server-side. Compose passes `TALLYVANE_GOOGLE_ADMIN_REDIRECT_URI` to the backend;
the local example is in `ops/auth-local.env.example`.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Admin login offers no methods | Confirm the address is in `TALLYVANE_ADMIN_EMAILS` and the persisted sign-in policy enables a method available on the server. For password/email-code sign-in, confirm the normal account is active and verified before opening the admin login page. |
| Password works in the app but not in admin | The admin account uses a separate credential row. Open the admin login page once to provision an eligible verified user account; after that, credential changes do not synchronize between realms. |
| Google button is missing | Confirm all three main Google settings and `TALLYVANE_GOOGLE_ADMIN_REDIRECT_URI` are set, the redirect URI exactly matches Google's registration, and the persisted admin sign-in policy enables Google. |
| Google returns an error | Check that the registered admin URI ends in `/api/v1/auth/admin/google/callback`, the reverse proxy forwards it to the backend, and the browser started OAuth from the admin hostname. |
| MFA is requested but the code is rejected | Use the factor named by the admin login page. Admin factors are independent after provisioning; changing or resetting the normal user's factors does not change the admin factors. |
| A previously signed-in admin is still authenticated after an allowlist edit | Allowlist changes do not revoke stored sessions. Sign out or revoke all admin sessions, then remove the address from the allowlist. |
