# ops

Deployment, migrations and backups for a single 2 GB VPS.

```
backup/       nightly dump, offsite upload, weekly restore verification
```

Migrations are deliberately absent from this tree — an earlier version listed them
here, contradicting the paragraph below and ARCHITECTURE.md §4.6. Each capability
carries its own under `db/migration/<module>/`, and `platform:persistence`
collects them (ADR-051).

## Containers

Five services: PostgreSQL, the Ktor server, two Next.js frontends
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
