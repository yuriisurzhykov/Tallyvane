# ops

Deployment, migrations and backups for a single 2 GB VPS.

```
migrations/   Flyway scripts, grouped by owning module
backup/       nightly dump, offsite upload, weekly restore verification
```

## Containers

Four services: PostgreSQL, the Ktor server, the Next.js frontend, and
`cloudflared`. Typst and cwebp are not containers — they are static binaries
inside the server image, invoked as short-lived processes.

## No inbound ports

`cloudflared` opens an outbound tunnel; traffic arrives through it. The
firewall can block every inbound port except SSH. Path routing happens at the
tunnel: `/api/*`, `/calendar/*` and `/media/*` go to Ktor, everything else to
Next, so Node never sits in the hot path of an API call.

## Memory budget

| Component | Budget |
| --- | --- |
| PostgreSQL | 384 MB |
| JVM | 576 MB (heap 384) |
| Node with Next.js | 320 MB |
| cloudflared | 48 MB |
| typst / cwebp at peak | 64 MB |
| Left to the OS and page cache | ~640 MB |

Limits are declared per service so a spike in one container cannot kill its
neighbours.

## Migrations belong to modules

Each capability owns its PostgreSQL schema and carries its own migration
directory; `platform:persistence` collects them into one ordered stream. You
can read the history of a module's schema without wading through everyone
else's.

## Backups are only real if restored

A nightly dump is the easy half. The weekly job that restores the latest dump
into a throwaway container, checks row counts and referential integrity, runs
every migration against it and reports to Telegram is the half that decides
whether the backup exists at all.
