# Authentication deployment (operator-run)

> **Release gate: this branch is not ready to deploy yet.** Password/email auth, recovery, Google
> OAuth code + PKCE, MFA enrollment/verification/removal, versioned policy persistence, server-side
> admin policy/reset APIs and authenticated Google link/unlink are implemented locally. The
> production-like localhost smoke test, live Google configuration, Cloudflare, Mailpit deployment,
> and complete integration/browser matrix still need verification. Scenario-control APIs and seeded
> demo accounts are not release requirements. Do not run
> the migration or deployment steps below until `docs/frontend/auth-implementation-progress.md`
> marks those items complete and the full test suite passes. The remaining sections record the
> agreed operator configuration for that later rollout.

These steps are instructions for the owner. No remote deployment, Cloudflare change, Google client creation, or production migration is performed by this change.

## Hostnames and access policy

| Host | Exposure |
| --- | --- |
| `surzhykov.icu` | Public website |
| `app.surzhykov.icu` | Temporary Cloudflare Access, owner only; application login still required |
| `admin.surzhykov.icu` | Existing Cloudflare Access plus application admin authorization |
| `mail.surzhykov.icu` | Cloudflare Access, owner only, entire hostname including API and WebSocket |

Before adding mail DNS, create a Self-hosted Access application for `mail.surzhykov.icu`, without a path restriction. Add an Allow policy with Include → Emails → `yuriisurzhykov@gmail.com`. Do the same for the temporary `app.surzhykov.icu` gate. Keep the existing admin gate. Enable an existing working login provider (email one-time PIN is sufficient). Do not add Everyone, Bypass, or a more-specific unprotected `/api` application. Test in an incognito browser with another email: it must be denied. [Cloudflare application setup](https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/self-hosted-public-app/) and [path precedence](https://developers.cloudflare.com/cloudflare-one/access-controls/policies/app-paths/).

Then add the proxied mail CNAME to the existing tunnel UUID's `.cfargotunnel.com` target using the same DNS configuration as app/admin. The checked-in tunnel template already routes mail to nginx. Do not create an A/AAAA record to the origin, publish container ports, or open 80/443/1025/8025 inbound. Keep the host firewall closed except existing SSH administration. Review other tunnels/routes for alternate origin access. `docker compose ps` should show no published ports in production.

Access is a separate edge login; it does not create an application session or grant an application admin role. Leave the public site outside these hostname-specific policies. The deployment smoke check now requires app/admin/mail challenges; update that assertion deliberately when removing the temporary app gate later.

## Prepare secrets before migration

On the server, in the existing deployment directory (normally `/srv/apps/tallyvane`), preserve the current `.env`, image tags, active colours, and tunnel credentials. Keep `.env` shell-compatible: `apply.sh` sources it. Store single-line structured values inside single quotes. Do not paste rendered `docker compose config` into logs; use `config --quiet`.

Add the settings documented in `ops/.env.example`:

- `TALLYVANE_TOKEN_PEPPER`: generate once with `openssl rand -hex 32`. Keep stable across replicas, restarts and rollback. Incrementing `TALLYVANE_TOKEN_PEPPER_VERSION` accompanies a deliberate pepper rotation and invalidates sessions.
- `TALLYVANE_TOTP_KEYSET`: persistent Tink JSON encryption keyset supplied by the backend key-generation procedure. Store its compact single-line JSON in a single-quoted `.env` assignment. Keep the same keyset for both colours. Back it up with the database; losing it makes enrolled TOTP secrets unreadable. Do not replace it with a random text password or generate a new key every boot.
- `TALLYVANE_ADMIN_EMAILS=yuriisurzhykov@gmail.com`: application bootstrap admin allowlist; use a verified account with this address.
- `TALLYVANE_SMTP_FROM=noreply@surzhykov.icu`.
- Leave all `TALLYVANE_GOOGLE_*` values empty until the OAuth client exists.

Production compose fixes auth enabled and secure cookies, restricts auth origins to HTTPS app/admin, and points SMTP exclusively at `mailpit:1025`. There is no SMTP relay, forwarding configuration, or internet route in Mailpit's internal network. Messages remain in the `tallyvane_maildata` volume until explicitly deleted; no automatic count/age expiry is configured. Monitor disk usage and archive/delete deliberately. Mailpit is pinned to [v1.31.1](https://github.com/axllent/mailpit/releases/tag/v1.31.1); [Docker persistence](https://mailpit.axllent.org/docs/install/docker/) and [runtime retention/network options](https://mailpit.axllent.org/docs/configuration/runtime-options/).

Generate a keyset once from the backend checkout, into a restricted file (the task never prints key material):

```bash
umask 077
cd backend
./gradlew :modules:identity:infrastructure:generateTotpKeyset -PkeysetOutput=/absolute/private/totp-keyset.json
```

Compact that JSON with `jq -c . /absolute/private/totp-keyset.json` into the single-quoted environment assignment using a secure editor. Treat the output as a secret; do not paste it into support logs. For the local demo generate a different file and set `export DEMO_TOTP_KEYSET="$(jq -c . /absolute/private/demo-totp-keyset.json)"` before invoking demo compose.

## Backup and rollback boundary

Before any `apply.sh`, rollout, or migration, make a restricted backup OUTSIDE the deployment directory (`deploy.sh` uses rsync --delete). Commands below are Bash, executed by the operator on the server:

```bash
cd /srv/apps/tallyvane
umask 077
backup_dir="/srv/backups/tallyvane/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$backup_dir"
cp .env "$backup_dir/deployment.env"
docker compose ps > "$backup_dir/containers.txt"
docker compose exec -T db pg_dump -U tallyvane -d tallyvane -Fc > "$backup_dir/database.dump"
test -s "$backup_dir/database.dump"
docker compose exec -T db pg_restore --list < "$backup_dir/database.dump" > "$backup_dir/database.contents"
```

Also back up `/srv/secrets/tallyvane/tunnel-credentials.json` into restricted encrypted/off-host storage, together with `.env` and the dump. Verify a restore in a separate database before relying on the backup. Do not copy raw live PostgreSQL files.

For Mailpit backups after first deployment, briefly stop Mailpit to obtain a consistent SQLite database including its WAL files, then copy the whole data directory and restart it:

```bash
docker compose stop mailpit
mkdir -p "$backup_dir/mailpit"
docker compose cp mailpit:/data/. "$backup_dir/mailpit/"
docker compose start mailpit
```

Email operations may fail during that pause; schedule it outside verification/reset testing. Check service health after restart. Protect mailbox backups: they contain verification/reset links and addresses.

Normal rollback preserves the additive migrated schema and uses the existing `bash apply.sh --rollback server` (and corresponding frontend service commands). Do not run an old migrate image or undo Flyway history. Keep old colours until smoke checks pass; `--retire` is an explicit later action. Database restore is a separate outage operation: stop both backend colours and public traffic, restore into a new database/volume from the verified dump with matching secret backup and old images, verify privately, then reconnect. Never restore destructively over the live database while either colour runs. Restoring a snapshot loses writes since that snapshot.

## Deployment order

1. Finish Access policies and owner/outsider tests, prepare secrets, and take the backups above.
2. Build/publish versioned images using the existing release workflow. Keep previous image tags recorded. Transfer the updated ops files using the existing deployment procedure; no custom demo compose is used on the server.
3. Validate `docker compose config --quiet`. The first deployment adding Mailpit/nginx/tunnel configuration requires the existing full `bash apply.sh` path (it starts Mailpit before nginx validation and renders the tunnel template). This runs migrations: do not invoke before backup. After that first apply, run `docker compose restart cloudflared` so its running process reloads the new mail ingress (a bind-mounted file change alone does not restart it). Subsequent image-only updates retain `bash apply.sh --rollout server`, `--rollout frontend-app`, and `--rollout frontend-admin`.
4. Check `docker compose ps`, `docker compose exec -T nginx nginx -t`, and readiness. Exercise app login before retiring old colours. No Mailpit port should be published.
5. Without Access cookies, `curl -I https://app.surzhykov.icu/`, admin, mail `/`, and mail `/api/v1/messages` must challenge through Cloudflare Access and return no inbox/API data. The public site must still return normally. Test disallowed email denial in a separate browser.
6. After the implementation gate is closed, update this smoke-test step to the final tested API surface. It must cover registration verification, every enabled sign-in/recovery/MFA path, policy edits, MFA reset and admin denial, plus persistence across backend and Mailpit restarts. Do not claim the deployment is validated until those checks pass against the actual rollout.

Application logs use the same structured event contract as local Docker. Production Compose selects
`OTEL_LOGS_EXPORTER=otlp` for the JVM and all three frontends; the server's Java agent bridges
Logback records, and Next.js runtimes register the OTLP Logs exporter. In Grafana Cloud Explore,
filter the `service.name` resource by `tallyvane-server`, `tallyvane-frontend-web`,
`tallyvane-frontend-app`, or `tallyvane-frontend-admin`. Deployment configuration must provide the
existing `OTEL_EXPORTER_OTLP_ENDPOINT` and `OTEL_EXPORTER_OTLP_HEADERS` secrets.

The mailbox is a capture inbox. A message addressed to Gmail is read in Mailpit; it is never delivered to Gmail. This deployment is therefore for controlled owner testing, not public signup delivery.

## Google OAuth (later, optional)

Create a Google Cloud Web application OAuth client after configuring its consent screen and testing audience. Add the owner as a test user while the consent screen is in Testing. Register exactly `https://app.surzhykov.icu/api/v1/auth/google/callback` as the authorized redirect URI and set that same value in `TALLYVANE_GOOGLE_REDIRECT_URI`. Set all three `TALLYVANE_GOOGLE_CLIENT_ID`, `TALLYVANE_GOOGLE_CLIENT_SECRET`, and `TALLYVANE_GOOGLE_REDIRECT_URI` together, keep the secret server-side, then roll out the backend. Google requires an [exact registered redirect URI](https://developers.google.com/identity/openid-connect/reference). Do not reuse Cloudflare Access's Google client: that callback belongs to the Access team domain. Keep email/password available while testing Google and confirm cancellation, invalid state, existing-account conflicts, and TOTP-required Google sign-in.

## Local authentication verification

Follow [auth-local-development.md](auth-local-development.md) for the complete Windows/PowerShell
setup: build all local images, configure `*.localhost` hostnames, generate an isolated TOTP key,
start PostgreSQL/backend/frontends/Mailpit, optionally configure Google, and walk through the
manual verification matrix. See [admin-login.md](admin-login.md) for admin identity provisioning,
login methods, and its Google callback. The local compose stack must remain separate from production
`.env` and production volumes.
