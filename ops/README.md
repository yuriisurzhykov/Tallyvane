# ops

Deployment, migrations and backups for a single 2 GB VPS.

```
docker-compose.yml    one service so far: db
init/                 runs once, when Postgres first creates its data directory
backup/               nightly dump, offsite upload, weekly restore verification
```

Migrations are deliberately absent from this tree — an earlier version listed them
here, contradicting the paragraph below and ARCHITECTURE.md §4.6, and an empty
`migrations/` directory outlived that correction until 2026-08-25. Each capability
carries its own under `db/migration/<module>/`, and `platform:persistence`
collects them (ADR-051).

## 2026-08-25 — what this file holds, and one withdrawn attempt

Values are written in `docker-compose.yml` itself, not in a `.env` it would
interpolate. **A committed `.env` is not an option in this repository**, and the
reason is not formality: it is the file people reach for when an API key needs
parking, and one lapse of memory publishes it. `.gitignore` excludes `.env`
everywhere. So anything secret appears in compose only as a `${VAR}` reference
with no default, resolved from the deploy's own environment, and everything
non-secret is written in the open where a reviewer meets it in the diff.

That rule invalidated a first attempt, recorded here because the wrong turn
explains the shape: the Postgres tag and settings were put in `ops/.env` for both
compose and the test fixture to read. `.gitignore` would have kept that file out
of every clone and out of CI, so the "single source" was unreachable — and the
mistake was building it before checking that. The tag is consequently still
written twice, here and in `PostgresFixture`, with nothing comparing them;
`backend/.plans/` carries it as an open debt.

The Postgres settings are here because §16.2's memory budget fixes them and,
before this file, they were a table in a document no running server had read.
`max_connections=20` is the one that bites: a connection is a backend process, and
one application pool holds its full size open at rest — eight, measured in
`backend/playground/pool-occupancy/`. Two copies during an update is 16 of the 20.

`init/` runs **only** when the data directory is first created. The bounds it
applies to the role — `statement_timeout`, `lock_timeout`,
`idle_in_transaction_session_timeout` — are defence in depth behind the ones
`PostgresPersistence` puts on its own connections (ADR-061); they cover `psql`,
the migrate command, and anything else that connects. Changing them needs the
volume recreated or the `ALTER ROLE` statements applied by hand. Verified on a
running container: `pg_roles.rolconfig` carried all three and a fresh session
inherited them.

## Containers

`docker-compose.yml` holds `db` and nothing else yet. The other services need images
that do not exist — there is no server distribution and no frontend build here — so
they arrive with them rather than as placeholders nobody can run. `db` came first
because it closed a debt rather than anticipating one.

Five services in total, when they exist: PostgreSQL, the Ktor server, two Next.js frontends
(`frontend-web`, built from `frontend-web/`; `frontend-admin`, built from
`frontend-admin/`), and `cloudflared`. Typst and cwebp are not containers —
they are static binaries inside the server image, invoked as short-lived
processes.

Two frontend containers rather than one is a deliberate change (ADR-032, and
see the ARCHITECTURE.md §3.2/§16 update it made): the admin surface needed to
be a genuinely separate application — no workspace dependency on the console,
so a developer cannot import one into the other by accident — not just a
different route inside the same process.

## No inbound ports

`cloudflared` opens an outbound tunnel; traffic arrives through it. The
firewall can block every inbound port except SSH. Routing happens at the
tunnel by hostname first, then by path within that hostname: `tallyvane.com`
and `app.tallyvane.com` go to `frontend-web` except for `/api/*`,
`/calendar/*` and `/media/*`, which go to Ktor; `admin.tallyvane.com` goes to
`frontend-admin` with the same exception for `/api/*` and `/media/*`, pointed
at the same Ktor container. Node never sits in the hot path of an API call,
in either frontend.

`admin.tallyvane.com` additionally sits behind Cloudflare Access — an
identity check the request has to clear before it ever reaches the tunnel's
ingress target. That reuses the tunnel already in place rather than adding a
VPN or any other new piece of infrastructure to operate.

## Memory budget

| Component | Budget |
| --- | --- |
| PostgreSQL | 384 MB |
| JVM | 576 MB (heap 384) |
| Node — `frontend-web` | 320 MB |
| Node — `frontend-admin` | 192 MB (estimate — single owner, low concurrency, smaller route tree; replace with a measured number once it's real) |
| cloudflared | 48 MB |
| typst / cwebp at peak | 64 MB |
| Left to the OS and page cache | ~464 MB (was ~640 MB with one Node process; ADR-032 spends some of that margin on admin isolation) |

Limits are declared per service so a spike in one container cannot kill its
neighbours.

## Migrations belong to modules

Each capability owns its PostgreSQL schema and carries its own migration
directory; `platform:persistence` collects them into one ordered stream. You
can read the history of a module's schema without wading through everyone
else's.

The stream is ordered globally, by a timestamp in each file name, and not module
by module. Cross-schema foreign keys are allowed and used, so `identity.users`
has to exist before `jobs` references it, and Flyway compares versions across
every location rather than within one. Applying them is a one-shot command this
deploy runs, not something the server does as it starts — which is why the
readiness probe can verify the schema instead of reporting on work it just did
itself (ADR-051).

## Backups are only real if restored

A nightly dump is the easy half. The weekly job that restores the latest dump
into a throwaway container, checks row counts and referential integrity, runs
every migration against it and reports to Telegram is the half that decides
whether the backup exists at all.
