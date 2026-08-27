# ops

Provisioning, deployment, migrations and backups for a single VPS.

```
provision/            one file per step of preparing a bare machine
  lib.sh              shared reporting, idempotent file writes, verified manual steps
  provision.sh        runs the steps in order
docker-compose.yml    cloudflared, nginx, db
deploy.sh             copies this directory to the server and brings the composition up
nginx/templates/      server blocks, substituted by the image's own entrypoint
nginx/www/            three placeholder pages, one per hostname
cloudflared/          the tunnel's ingress template
init/                 runs once, when Postgres first creates its data directory
.env.example          the shape of the file that lives on the server
```

Backups are not in this tree. They are needed and unbuilt — see the section below.

Migrations are deliberately absent from this tree — an earlier version listed them
here, contradicting the paragraph below and ARCHITECTURE.md §4.6, and an empty
`migrations/` directory outlived that correction until 2026-08-25. Each capability
carries its own under `db/migration/<module>/`, and `platform:persistence`
collects them (ADR-051).

## Two commands, and the line between them

`provision/` prepares a machine: an unprivileged account, key-only SSH, a firewall,
fail2ban, Docker, two directories, the tunnel. It runs **on the server**, once, as
root, and it is idempotent — a second run reports what is already true rather than
doing it again.

`deploy.sh` ships this directory to that machine and starts the composition. It runs
**on your own machine**, from a clone, as often as you like. The server holds no
source code: it holds a copy of `ops/`, a `.env`, and one secret.

The line between them is what changes and how often. Provisioning describes the
machine, and if the machine is replaced it runs again from the top. Deployment
describes the application, and it runs on every change.

## Secrets live outside the tree that gets synced

`deploy.sh` syncs with deletion: anything on the server that is not in the
repository is removed. That is the point — it is how a server block deleted here
stops being served there, rather than lingering for a year because nobody
remembered it existed.

Which makes the placement of secrets a correctness question, not a preference.
The tunnel credentials sit in `/srv/secrets/tallyvane/`, outside the synced tree,
where deletion cannot reach them. `.env` stays in the deployment directory and is
the single `--exclude` — one exception, small enough to remember, and it keeps a
`docker compose ps` typed by hand on the server working without extra flags.

A committed `.env` is not an option in this repository, and the reason is not
formality: it is the file people reach for when an API key needs parking, and one
lapse of memory publishes it. `.gitignore` excludes `.env` everywhere. So anything
secret appears in compose only as a `${VAR}` reference with no default, and
everything non-secret is written in the open where a reviewer meets it in the diff.

## Manual steps are verified, not trusted

Three things in provisioning cannot be done by a script: a browser login to
Cloudflare, a public key only the operator has, and a setting in the hosting
provider's control panel.

`manual_step` in `lib.sh` prints instructions, waits, and then **runs a check**. It
loops until the check passes. It also runs the check *first*, which is what makes
the whole run idempotent: on an already-provisioned machine nothing is asked at all.

`manual_notice` exists for the one case where no check is possible from this
machine — the provider's firewall. It says so instead of inventing a check that
always passes, because an unverified step that looks verified is worse than one
that admits it.

## Containers

`docker-compose.yml` holds `cloudflared`, `nginx` and `db`. The Ktor server and the
two frontends join it when their images exist; there is no Dockerfile in this
repository yet, so a service for them here would be a placeholder nobody can run.

Two frontend containers rather than one is a deliberate change (ADR-032, and
see the ARCHITECTURE.md §3.2/§16 update it made): the admin surface needed to
be a genuinely separate application — no workspace dependency on the console,
so a developer cannot import one into the other by accident — not just a
different route inside the same process.

Typst and cwebp are not containers — they are static binaries inside the server
image, invoked as short-lived processes.

## No inbound ports

`cloudflared` opens an outbound tunnel; traffic arrives through it. The firewall
blocks every inbound port except SSH, and the DNS records carry Cloudflare's
addresses rather than this machine's, so the server's address does not appear
anywhere a visitor can read.

`admin.<domain>` additionally sits behind Cloudflare Access — an identity check the
request has to clear before it ever reaches the tunnel's ingress target. That
reuses the tunnel already in place rather than adding a VPN or any other new piece
of infrastructure to operate.

## Routing is nginx's job, not the tunnel's

Every hostname in `cloudflared/config.yml.template` points at the same nginx, and
nginx decides what answers. The tunnel could route by hostname itself, and an
earlier version of this document said it did — but then routing would live in two
places, and leaving Cloudflare later would mean rewriting whichever half stayed
behind. Path routing (`/api/*` to Ktor, everything else to a frontend) has no
equivalent in the tunnel at all.

Server blocks are `nginx/templates/*.conf.template`, and the image's own entrypoint
substitutes `${TALLYVANE_DOMAIN}` into them at startup — so the domain is
configuration, not a value edited in three files. `default.conf.template` is named
that way on purpose: it overwrites the image's own `conf.d/default.conf`, whose
server block is the "Welcome to nginx!" page that would otherwise answer every
unmatched hostname.

## The access log is one line of JSON per request, on stdout

`log_format tallyvane` in `00-common.conf.template`. It goes to stdout because
compose's `logging:` governs stdout and stderr and nothing else — choosing a bound
in compose is choosing to log there.

`CF-Connecting-IP` becomes `$remote_addr` via `set_real_ip_from`, and this is not
cosmetic: behind the tunnel every request arrives from the cloudflared container, so
without it the log would record one address for the whole internet, and any
per-address rate limit would treat the world as a single client.

## Memory budget

Numbers below are ARCHITECTURE.md §16.2's, written for a 2 GB machine. The machine
is 3 GB with 100 GB of disk (stated by the author, 2026-08-26), and recalculating
this table is an open item in `backend/.plans/backend-infra-cache-wiring.md` — it is
left as it stands rather than adjusted by guesswork.

| Component | Budget |
| --- | --- |
| PostgreSQL | 384 MB |
| JVM | 576 MB (heap 384) |
| Node — `frontend-web` | 320 MB |
| Node — `frontend-admin` | 192 MB (estimate — single owner, low concurrency, smaller route tree; replace with a measured number once it's real) |
| cloudflared | 48 MB |
| typst / cwebp at peak | 64 MB |
| Left to the OS and page cache | ~464 MB (was ~640 MB with one Node process; ADR-032 spends some of that margin on admin isolation) |

`mem_limit` is declared for `db` only. nginx and cloudflared carry none yet: the
numbers are supposed to come from `docker stats` against these containers, and a
guessed limit is a limit that kills a working process for no reason.

## What this tree deliberately does not do yet

- **Rate limits.** nginx is the right place for the coarse per-address kind, but the
  thresholds are an open decision, and a limit invented here would be a number with
  nothing behind it. The accurate per-account kind belongs to the application and
  needs a counter store.
- **Timeouts.** Left at nginx's defaults. They matter once something is being
  proxied, and nothing is yet.
- **Host configuration as data.** `provision/` is idempotent shell, not a
  declarative description. That is a deliberate stop: it needs no new tool, and it
  covers a single machine. A second machine is the point at which this becomes the
  wrong shape.

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

## Backups are only real if restored, and there are none yet

Nothing in this tree backs anything up. ARCHITECTURE.md §16.1 lists a backup service
and `backend/.plans/backend-infra-cache-wiring.md` carries the design as an open
item: what is copied, where to, how often, how long it is kept, and how a restore is
checked. An earlier version of this document listed a `backup/` directory as though
it existed, which is the same defect as the empty `migrations/` directory recorded
above — a reader had no way to tell a plan from a fact.

The deadline is not arbitrary. The database this deploy starts is empty: no
migrations have run and no module owns a table yet, so there is nothing to lose. That
changes with `identity`, and a backup written after the first data it is supposed to
protect is a backup with a gap in it.

The intent, when it is built: a nightly dump is the easy half. The weekly job that
restores the latest dump into a throwaway container, checks row counts and
referential integrity, runs every migration against it and reports to Telegram is the
half that decides whether the backup exists at all.

## The SOLID angle, honestly

Two of the five apply here and the rest do not, and saying so is more useful than
stretching the vocabulary.

**Single responsibility** is the organising idea of `provision/`: one file per step,
each with one concern and one verification. It is why `--from 50` is a resume rather
than a rerun, and why a failure names a step instead of a line number.

**Interface segregation** describes the two networks. `edge` and `data` exist so
that cloudflared is given reachability to nginx and nothing more, and PostgreSQL is
reachable only by what needs it. A single flat network would grant every container
the whole graph.

Open/closed, Liskov substitution and dependency inversion are about type
relationships. This tree has no types. `lib.sh` is shared code, not an abstraction
with implementations behind it, and calling it one would be a costume.

## 2026-08-25 — what docker-compose.yml holds, and one withdrawn attempt

A first attempt put the Postgres tag and settings in `ops/.env` for both compose and
the test fixture to read. `.gitignore` would have kept that file out of every clone
and out of CI, so the "single source" was unreachable — and the mistake was building
it before checking that. The tag is consequently still written twice, here and in
`PostgresFixture`, with nothing comparing them; `backend/.plans/` carries it as an
open debt.

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

## 2026-08-27 — the deployment existed only on the server, for an hour

The edge was brought up by hand over SSH: a `compose.yml`, a `cloudflared`
directory and a `.env` typed into a session, none of it in the repository. It
worked, and it was unreproducible — if the machine had been lost, the only record
of how it was built was a chat log. This tree is that hour, moved into version
control before it grew into something expensive to move.

Two facts made the layout a decision rather than a copy. `rsync` without deletion
leaves removed configuration serving traffic forever; `rsync` with deletion would
have wiped the tunnel credentials, which exist nowhere else. Hence secrets outside
the synced tree, `.env` as the single exclusion.

## 2026-08-27 — nginx will not start for an upstream that does not exist

Measured, because the answer decides how server blocks are written for services
whose images do not exist yet.

A literal name in `upstream` or `proxy_pass` is resolved once, at startup. With the
host absent, nginx does not answer errors — it refuses to start:
`[emerg] host not found in upstream "app:8080"`, and `nginx -t` fails with it. Put a
variable in `proxy_pass` and resolution moves to request time: the configuration
validates, nginx starts, and the request gets a 502.

The variable form was rejected. It also switches off everything the named group is
for — connection reuse to the backend, load balancing, ejecting a server that stopped
answering — and it makes a typo in a service name indistinguishable from a service
that is down. So proxy routes are added together with the service they point at, and
`deploy.sh` validates the configuration before applying it.

That validation runs the image's real entrypoint (`docker compose run --rm nginx
nginx -t`), not `nginx -t` against the template directory. Checking templates would
prove nothing: the file nginx reads does not exist until the entrypoint writes it.
Confirmed to fail on a deliberately broken template, rather than assumed to.

## 2026-08-27 — a missing domain is invisible to nginx, and compose is the guard

`NGINX_ENVSUBST_FILTER` confines substitution to `TALLYVANE_`-prefixed variables,
which is what keeps envsubst away from nginx's own `$remote_addr` and friends. It has
a consequence worth knowing: when the variable is simply absent, envsubst never
learns the name and leaves `${TALLYVANE_DOMAIN}` in the output **verbatim** — and
nginx accepts that as a perfectly valid `server_name`. Measured: configuration test
successful, three server blocks answering to literal placeholder names, every real
hostname falling through to the default server and getting a 404.

So the check that catches this is `${DOMAIN:?DOMAIN must be set}` in
`docker-compose.yml`, one layer up, verified to refuse: *required variable DOMAIN is
missing a value*. That reference must never grow a default.

## 2026-08-27 — cloudflared runs as 65532, and that replaced `user: root`

The first working version of the tunnel service carried `user: root`, because the
credentials file belonged to the deploy account and the image's own user could not
read it. Read from the image rather than remembered:
`Config.User` is `65532:65532`, and the image has no shell at all — `sh` is not on
its filesystem.

The file is therefore owned by 65532 with mode 400, and `user: root` is gone. The
trade it avoided: granting a process that holds a permanent connection to the
internet full authority inside its container in order to read one file.

## 2026-08-27 — a 522 that was a leftover DNS record, and one flag that did not help

The first request to the apex through the finished tunnel answered
`HTTP/2 522`. The status is the diagnosis: 522 means Cloudflare tried an origin
**address** and could not connect, where a tunnel that nobody answers gives 1033 or
502. Cloudflare was going straight to port 443 of the machine, which the firewall
correctly refuses.

The cause was an `A` record for the apex that Cloudflare imported when the zone was
added, pointing at the server, proxied. `cloudflared tunnel route dns` refused to
replace it, and so did `--overwrite-dns`: *code 1003 — an A, AAAA, or CNAME record
with that host already exists*. The flag replaces one record and cannot resolve
several with the same name, which is what a zone transfer tends to produce.

Deleting that one record by hand and re-running `route dns` fixed it. Worth writing
down for next time: in that dashboard, only `A`, `AAAA` and `CNAME` for the hostname
being routed may be deleted. `MX` and `TXT` are the domain's mail — SPF, DMARC,
autodiscover — and removing them breaks mail rather than a web page. In this zone
there was no `MX` at all, which means mail is not being received; noted, not acted on.

## 2026-08-27 — a server-level access_log replaces, an http-level one adds

The base `nginx.conf` in the official image already sets `access_log ... main` at
http level, so a second http-level directive would have produced two lines per
request in two formats. Directives at the same level accumulate; one at a nested
level replaces what it inherits. Hence `access_log` inside each server block.

Measured rather than reasoned about: eight requests produced eight JSON lines and
zero lines in the `main` format.
