# Production release: owner checklist

This is the short operator checklist for the current authentication branch. Use
[auth-deployment.md](auth-deployment.md) for the commands, backup and rollback procedure,
[admin-login.md](admin-login.md) for first administrator provisioning, and
[`ops/.env.example`](../../ops/.env.example) for every server setting. **Do not publish a release tag
until the release gates below are green and the production backup has been verified.** A publish tag
starts the matching GitHub Actions deployment automatically.

## 1. Release gates before touching production

- Review the final changes, including uncommitted files, then commit the intended release state.
- Run `pnpm install --frozen-lockfile`, `pnpm run api:check`, `pnpm run typecheck`, `pnpm run lint`,
  `pnpm run arch`, `pnpm run test`, and `pnpm run build` from the repository root.
- Run `./gradlew check --no-daemon` and `./gradlew integrationTest --no-daemon` from `backend` with
  Java 21 and Docker available. The integration task is separate from `check`.
- Run `pnpm --dir frontend-web test:auth-app` against the isolated local auth stack. Walk through
  registration, verification, login and recovery, factor enrollment and removal, policy edits,
  admin bootstrap/login/reset, and persistence after service restart. Check the real Google flow if
  Google login will be enabled in production.
- Confirm the relevant GitHub CI jobs passed for the release commit. A passing build or static
  check alone does not prove the browser and database flows.

Record any failed or unrun gate and postpone the production tag until it is resolved.

## 2. Prepare the server and external services

1. Keep the existing deployment directory (normally `/srv/apps/tallyvane`) and its `.env`.
   Provision a new host with the existing `ops/provision/` process before deploying.
2. Configure Cloudflare Tunnel DNS and Access for the app, admin and mail hostnames as specified in
   [auth-deployment.md](auth-deployment.md). Verify an owner login and denial for another address.
   The public website remains public. Do not expose container ports directly.
3. Set the required `.env` values from `ops/.env.example`: image tags, PostgreSQL password, health
   token, OTLP endpoint and headers, stable token pepper and version, persistent TOTP keyset, admin
   email allowlist and SMTP settings. Generate the TOTP keyset once with the documented Gradle task;
   back it up with the database. Keep the production values separate from `ops/auth-local.env`.
4. Decide whether Google login is part of this release. If it is, register the exact app and admin
   callback URIs in the Google Web OAuth client and set the matching server values. Otherwise leave
   the Google values empty and verify the UI does not offer Google login.
5. Make a restricted, verified PostgreSQL backup **outside** the directory synchronized by
   `deploy.sh`; back up `.env` and tunnel credentials too. Follow the restore test in
   [auth-deployment.md](auth-deployment.md). Record currently active image tags and colours.

## 3. Publish and verify

1. Publish versioned backend, frontend web, app and admin images only from the verified commit.
   The matching `backend-v*` and `frontend-*-v*` tag workflows publish and deploy their service.
   Check the workflow result and the image tag before assuming the server has changed.
2. For the first rollout that changes Mailpit, nginx or tunnel configuration, transfer the current
   `ops/` files and use the full `bash apply.sh` path described in [auth-deployment.md](auth-deployment.md).
   It runs database migrations. Restart `cloudflared` after the new mail ingress is installed.
   For later image-only updates, use the service rollout paths.
3. On the host run `docker compose config --quiet`, inspect `docker compose ps`, validate nginx,
   and check backend readiness and application logs. Confirm no production service publishes a host
   port. Verify Cloudflare Access challenges app, admin and mail while the public site loads.
4. From a fresh browser, register and verify an ordinary user through Mailpit, sign in, exercise
   recovery and MFA, then open admin `/login` with an allowlisted verified address to provision its
   separate admin identity. Confirm a non-allowlisted account cannot enter admin. Test the enabled
   policy, factor removal and admin reset paths, then restart the backend and Mailpit and check
   persistence. Mailpit captures mail; it does not deliver to Gmail.
5. Keep the old colours and previous tags until these checks pass. If the new service fails, use
   `bash apply.sh --rollback <service>` as described in the deployment guide. Do not reverse Flyway
   migrations or restore a database over running services.

## Release record

Write down the release commit and four image tags, CI run URLs, backup location and restore-test
result, migration result, owner/outsider Access results, browser smoke-test results, and rollback
decision. The operator supplies production credentials and performs the server changes; this
repository contains neither those credentials nor proof that production has been changed.
