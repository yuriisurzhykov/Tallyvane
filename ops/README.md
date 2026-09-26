# ops

Provisioning, deployment, migrations and backups for a single VPS.

```
provision/            one file per step of preparing a bare machine
  lib.sh              shared reporting, idempotent file writes, verified manual steps
  provision.sh        runs the steps in order
docker-compose.yml    cloudflared, nginx, db
deploy.sh             runs on your machine: copies this directory to the server, then calls apply.sh
apply.sh              runs on the server: generates, validates, starts, reports; also --rollout/--retire
deploy-wrapper.sh      the one command CI's forced-command SSH key may run
nginx/templates/      server blocks, substituted by the image's own entrypoint
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

`docker-compose.yml` holds `cloudflared`, `nginx`, `db`, the Ktor server (`server`/`migrate`)
and, as of 2026-08-28, all three Next.js applications: `frontend-web`, `frontend-app` and
`frontend-admin`.

Three frontend containers rather than one is a deliberate progression, not a single
decision — ADR-032 split `frontend-admin` off (the admin surface needed to be a genuinely
separate application, no workspace dependency on the console, so a developer cannot import
one into the other by accident — not just a different route inside the same process), and
ADR-065 later split the console itself into `frontend-app` for a different reason: it grew
its own server-rendering and Server-Sent-Events requirements, no longer borrowed from
`frontend-web`'s, and a shared container meant a shared `mem_limit` for two surfaces with
genuinely different load profiles. See ARCHITECTURE.md §3.2/§16 for both updates.

Typst and cwebp are not containers — they are static binaries inside the server
image, invoked as short-lived processes.

As of 2026-08-28, `server` and each of the three frontends is declared twice —
`server-blue`/`server-green`, `frontend-web-blue`/`frontend-web-green`, and so on — for
per-service blue-green (the CD plan's §2-3). Exactly one colour of each pair actually
runs in steady state; the other exists only in the compose file, behind
`profiles: [blue-green]` so a bare `docker compose up -d` cannot start it by accident.
`apply.sh --rollout <service>` is the only thing that starts the idle colour, cuts
nginx over to it, and `apply.sh --retire <service>` is the only thing that stops the
one being replaced — see the two dated entries below for why nginx's own upstream
declarations, not `apply.sh`'s choice of which colour to start, turned out to be the
binding constraint on that shape. `apply.sh --rollback <service>` cuts back to
whichever colour `--rollout` last left running, without a tag typed by hand — the
2026-08-31 entry below has the reasoning.

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

Numbers below are ARCHITECTURE.md §16.2's, written for a 2 GB machine. Docker reports
**3.768 GiB** on the real one (measured 2026-08-27; it had been carried as "3 GB" from
a figure stated in conversation), and recalculating this table is an open item in
`backend/.plans/backend-infra-cache-wiring.md` — it is left as it stands rather than
adjusted by guesswork.

A 2 GB swap file backs all of it, with `vm.swappiness` at 20 — insurance against a
spike becoming a kill, not a routine tier of memory. The `swap` step in `provision/`
has the reasoning, including why no container is forbidden to swap.

| Component                     | Budget                                                                                                                     |
|-------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| PostgreSQL                    | 384 MB                                                                                                                     |
| JVM                           | 576 MB (heap 384)                                                                                                          |
| Node — `frontend-web`         | 256 MB (ADR-065: public pages only, cache-friendly)                                                                        |
| Node — `frontend-app`         | 384 MB (ADR-065: partial SSR plus held-open SSE connections)                                                               |
| Node — `frontend-admin`       | 192 MB (estimate — single owner, low concurrency, smaller route tree; replace with a measured number once it's real)       |
| cloudflared                   | 48 MB                                                                                                                      |
| typst / cwebp at peak         | 64 MB                                                                                                                      |
| Left to the OS and page cache | shrinks further with each frontend split; this table has not been recalculated against the real 3.768 GiB machine below it |

All three Node numbers are estimates of the same kind ADR-032 already gave
`frontend-admin`'s — to be replaced by a `docker stats` reading once each application
carries real traffic. One measurement already exists and is worth recording here rather
than only in `docker-compose.yml`'s comments: under both a 192m and a 384m `mem_limit`,
Node's own `v8.getHeapStatistics().heap_size_limit` reported roughly 259 MB either way,
not a number that tracked the container limit precisely. The kernel still enforces
`mem_limit` regardless of what V8's own ceiling says, so this does not weaken the limit —
it just means V8's ceiling cannot be read as a precise dial for a container this small.

`mem_limit` is declared for `db`, `server` and all three frontends. nginx and cloudflared
carry none yet: the numbers are supposed to come from `docker stats` against these
containers, and a guessed limit is a limit that kills a working process for no reason.

## Continuous deployment: a forced-command key, not the administrator's own

Every tagged push that publishes an image (`.github/workflows/publish-*.yml`) also deploys it,
through `.github/actions/deploy-service` and one SSH key that exists for no other purpose.

That key is a **second** entry in the same `deploy` user's `authorized_keys`
(`ops/provision/25-ci-key.sh`), not a second user. `ops/provision/60-docker.sh` already
documents why: the `docker` group `deploy` needs in order to run `docker compose` is
root-equivalent on this host regardless — a second, sudo-less user would still need that same
group, so it would not have added the isolation it looks like it would. What actually contains
the key is `restrict,command="…/deploy-wrapper.sh"`: `restrict` (OpenSSH 7.2+) strips
port/X11/agent forwarding, a pty and `~/.ssh/rc` in one word, and the forced command means sshd
runs `deploy-wrapper.sh` no matter what the client asked to run — a leaked key can end up
nowhere but there.

`deploy-wrapper.sh` reads what the client *actually* asked for from `$SSH_ORIGINAL_COMMAND` — two
words, a service name and an image reference — and treats both as untrusted input rather than
something to `eval`: a service name against a fixed list, an image reference against an
allow-list of characters, because that value is written into `.env`, which `apply.sh` later
`source`s, and a shell metacharacter surviving into that file would run as this user the next
time `apply.sh` starts. Only once both check out does it update `.env` and call `apply.sh
--rollout <service>`.

Three secrets the workflow needs, none of them the administrator's own credentials:

| Secret                      | Holds                                                                                                                                                                                      |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `TALLYVANE_DEPLOY_KEY`      | The private half of the keypair `25-ci-key.sh` asks for — generated for this purpose only.                                                                                                 |
| `TALLYVANE_DEPLOY_HOST`     | `deploy@<host>`, the same form `deploy.sh`'s own `TALLYVANE_SSH` takes.                                                                                                                    |
| `TALLYVANE_SSH_KNOWN_HOSTS` | The server's host key, from `ssh-keyscan <host>` — pinned rather than accepted on first connect from a GitHub-hosted runner, which has no prior relationship with this server to trust on. |

## What this tree deliberately does not do yet

- **The accurate, per-account rate limit.** `00-common.conf.template`'s `limit_req_zone` (10
  req/s per address, `burst=20 nodelay`, confirmed as a conscious estimate rather than a
  measurement — there is no real traffic yet to measure) is the coarse, per-address layer only.
  The accurate kind belongs to the application, keyed by session rather than address, and needs
  a counter store that does not exist yet (§11.2).
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
each with one concern and one verification. It is why `--from docker` is a resume rather
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

## 2026-08-27 — the first deployment from the repository, and what it proved

`deploy.sh` ran end to end against the real machine: files copied, the tunnel's
configuration generated, the nginx configuration validated before being applied, three
containers started, and the smoke check answered `200` on all three hostnames with
**three different surface markers** — `site`, `app`, `admin`.

That last part is the whole point of the markers. Earlier the same day three hostnames
answered `200` through the tunnel while all three were being served by the same
built-in nginx welcome page. Status codes proved the path; only the markers proved
routing.

**The real client address works, and this is the check the local probe could not
make.** When the templates were written, `set_real_ip_from` could only be reasoned
about: there was no Cloudflare in front of a container on a laptop and therefore no
`CF-Connecting-IP` header. Against the deployed stack the access log carries the
visitor's own address — an IPv6 address from a browser, an IPv4 one from `curl` on
another machine — where without it every line would have read as the cloudflared
container's address, and any future per-address rule would have treated the whole
internet as one client.

**Memory at rest**, from `docker stats` with no traffic and no database connections:
cloudflared 16.8 MiB, nginx 5.0 MiB, PostgreSQL 26.6 MiB of its 384 MiB limit.

These are a floor, not a working set, and deliberately not used as `mem_limit` values.
`shared_buffers` is 192 MB that PostgreSQL maps as it needs it; nginx has served a
handful of requests; nothing has exercised any of them. A limit derived from an idle
reading is a limit that kills a working process the first time it does its job. nginx
and cloudflared therefore still carry no limit at all — with 3.7 GiB present and about
50 MiB in use, there is nothing yet for a limit to protect anything from. The number
becomes meaningful when the JVM and two Node processes exist, and it has to be measured
under load then.

**What the machine spends before we do.** With the three containers holding about 50 MiB
between them, `free -h` reports 903 MiB used of 3.8 GiB and 2.9 GiB available. That
roughly 850 MiB is the kernel, the Docker daemon, containerd, journald and fail2ban —
none of which appears in a budget table written per service. It is the number that makes
the recalculation worth doing rather than guessing: against 2.9 GiB available, the
planned JVM, two Node processes and PostgreSQL come to about 1.5 GiB and fit with room,
where against a nominal "3 GB" they looked tight.

Swap is a 2 GB file at `vm.swappiness = 20`, verified by reading
`/proc/sys/vm/swappiness` rather than the file that sets it, and present in `/etc/fstab`
so it survives a reboot.

**The role bounds are on the real database**, not only in the local probe:
`pg_roles.rolconfig` reads
`{statement_timeout=15s,lock_timeout=3s,idle_in_transaction_session_timeout=60s}`.
They were applied by `init/` at the moment the volume was created, which is the only
moment they can be applied without recreating it.

One incidental observation from the log, worth knowing before `/api` exists: a request
for `/api/v1/health/` was answered `404` after nginx looked for
`/srv/www/site/api/v1/health/index.html`. Every path on every hostname is currently
served from the static root. That changes when a proxy route is added, and that is when
an `upstream` block appears in this tree for the first time.

## 2026-08-27 — Cloudflare Access on the admin hostname, and what the smoke check asserts

`admin.<domain>` is a Cloudflare Access self-hosted application. A request without a
session never reaches the tunnel: Cloudflare answers `302` to a login page on the team
domain, together with `www-authenticate: Cloudflare-Access`. Login is by email one-time
PIN, with Google as a second method.

The order was one-time PIN first, then the application and its policy, then Google —
deliberately, so that the hostname was protected before anything depended on an external
identity provider working. A detail from Cloudflare's own documentation that is easy to
lose: **the one-time PIN provider has to be added before any policy exists**, or a visitor
sees the email field and never receives a code.

The team domain is auto-generated — `wispy-credit-8503.cloudflareaccess.com` here — and it
is embedded in the Google OAuth client's authorised origin and redirect URI. Renaming the
team after configuring Google means editing Google again, which is why the name is worth
settling first.

`deploy.sh` asserts the opposite thing for this hostname than for the other two. The two
public surfaces must answer `200` with their marker; admin must answer with a challenge
**and** must not return the page. Both halves matter: a check that only looked for a
challenge would still pass if Access were removed and something else happened to redirect.

It matches on `www-authenticate: Cloudflare-Access` or on a redirect to
`cloudflareaccess.com`, accepting either. One alone is brittle — the header is the current
shape and the redirect the older one — and matching the redirect's full target would tie
the check to the team name, which is configuration rather than a property of being
protected.

The condition was exercised against the real response and three fabricated ones before
being trusted: Access removed with the page served, Access removed with an empty body, and
a challenge present while the page leaked anyway. The first passes, the other three fail,
including the last — which is the case a careless version of this check would have missed.

Access itself is not in this repository. It is dashboard configuration, and describing it
as code would mean Cloudflare's API or Terraform; neither is set up, and this is recorded
as a gap rather than left to be discovered.

## 2026-08-27 — a server-level access_log replaces, an http-level one adds

The base `nginx.conf` in the official image already sets `access_log ... main` at
http level, so a second http-level directive would have produced two lines per
request in two formats. Directives at the same level accumulate; one at a nested
level replaces what it inherits. Hence `access_log` inside each server block.

Measured rather than reasoned about: eight requests produced eight JSON lines and
zero lines in the `main` format.

## 2026-08-28 — a `map` naming an upstream block still isn't caught by `nginx -t` on a typo

Blue-green (§16, the CD plan) needs `proxy_pass` to point at whichever of two colours is
currently active, and a `map`-selected variable whose value happens to equal the *name* of a
statically-declared `upstream` block is a different mechanism from the bare-hostname-in-a-variable
form already rejected above — worth re-testing on its own rather than assumed to share that
form's failure mode just because both put a variable in `proxy_pass`.

Built two throwaway `nginx:alpine` containers as upstreams (`backend-blue`, `backend-green`,
distinguished by their own body), one edge container with `upstream app_blue`/`app_green`, `map
$active_color $group { default app_blue; green app_green; }`, and `proxy_pass http://$group;`.

**Keepalive is genuinely preserved when the map's value matches a real upstream name.** Six
requests through the edge container to the correctly-mapped colour, read back from the
*upstream's own* access log (`$connection`/`$connection_requests`, not the edge's): all six landed
on `connection=1`, `connection_requests` counting 1 through 7 — one TCP connection, reused, not a
new handshake per request. This is the behaviour the plan hoped a live test would confirm rather
than assume, and it did.

**A typo in the `map` is not caught by `nginx -t`, and that is the deciding fact.** Changing the
`green` branch to a nonexistent name (`app_gren`) and running `nginx -t` reported *"configuration
file test is successful"* — the exact silence the bare-hostname form was rejected for. The failure
surfaces only at request time: `502`, with `no resolver defined to resolve app_gren` in the error
log — nginx falls back to trying to resolve the unmatched string as a real hostname, exactly the
dynamic-resolution path the earlier rejection already ruled out, once the string stops matching a
known upstream. A `map` does not get checked against the upstream names declared in the same file
at `nginx -t` time, so this is indistinguishable, at build time, from a typo in the bare-hostname
form — a typo would only be caught once nginx guesses wrong.

For contrast, the same typo written as a **literal** `proxy_pass http://app_gren;` (no variable at
all) fails `nginx -t` immediately: `host not found in upstream "app_gren"`, exit code 1 — confirming
the 2026-08-27 finding above still holds and pointing at the actual fix.

**Decision: blue-green switches colour by regenerating the literal `proxy_pass` target, not by a
`map`-selected variable.** The variable form is measurably no safer than the one already rejected
on the one property that matters most here — a typo distinguishable from a real outage before
traffic ever reaches it — despite genuinely solving the keepalive question in its favour. `apply.sh
--rollout <service>` rewrites the hostname template's `proxy_pass` line with the new colour's
literal upstream name and runs the existing `nginx -t` validation before `nginx -s reload`, the same
two-step deploy.sh/apply.sh already uses for every other nginx change.

## 2026-08-28 — declaring both colours permanently would double every memory budget in this file, so the rollout adds and removes a
`server` line instead

Naming `app_blue`/`app_green` as two permanently-declared `upstream` blocks (the shape this
document's own §2 draft assumed at first) turned out to have a consequence the earlier finding
above did not cover: **every `server` line nginx has ever been told about is resolved when the
config loads, whether or not any `location` actually sends it traffic.** Measured directly —
declared an `upstream` with one server pointing at a genuinely running container and a second
pointing at one that was merely `docker stop`ped, with **no** `location` in the file referencing the
second one at all: `nginx -t` still failed, `host not found in upstream`, on the unreferenced line.
Marking that same line `down` did not change the result — `down` tells nginx not to *send* traffic
there, not to skip resolving it.

That rules out the two-upstream-blocks shape outright for this deployment. Declaring `app_blue` and
`app_green` (and the six more pairs for the three frontends) permanently would require **both**
containers of **every** rollout-eligible service running at all times, not only during a rollout's
cutover window — doubling `app`, `frontend-web`, `frontend-app` and `frontend-admin`'s combined
memory footprint permanently, which was never the number this deployment's headroom was checked
against (§ above and `backend-infra-cache-wiring.md` both size for one copy of each, briefly two
during a rollout — never two, indefinitely, of all four at once).

**What holds up instead, verified against real containers through a full cutover:** the upstream
group keeps its *one* existing name (`backend`, `frontend_web`, …) forever — `proxy_pass` in the
three hostname templates never changes at all. What `apply.sh --rollout` edits is only the list of
`server` lines *inside* that one already-existing block, in three steps, each its own `nginx -t` +
`nginx -s reload`:

1. Start the idle colour's container. Add its `server` line, marked `down`. Reload — traffic
   provably stays 100% on the active colour (verified: five requests, all five still answered by
   the old colour, not a blend).
2. Cutover: in one reload, remove `down` from the new colour's line and add it to the old colour's.
   Verified atomic, not a blend — five requests immediately after answered 100% from the new colour,
   zero from the old one, with no round-robin mixing observed at any point.
3. Once the grace period ends: remove the old colour's line entirely, reload — **before** stopping
   its container. Verified that reload succeeds. Only then is the old colour's container stopped;
   verified that a *later*, unrelated reload still succeeds afterwards, because nothing in the
   config still names it.

Getting the order of steps 3's two halves backwards is exactly the bug this section exists to avoid:
stop the container first and nginx refuses to reload for *every* hostname on the next unrelated
change, not only the one whose colour was being retired.

## 2026-08-28 — the backend's service renamed from `app` to `server`

The backend's Gradle module (and everything in `ops/` mirroring its name — `app`/`app-blue`/`app-green`
in `docker-compose.yml`, `apply.sh`'s and `deploy-wrapper.sh`'s `app` case, `TALLYVANE_APP_PRIMARY`/
`SECONDARY`) was renamed to `server` to stop reading as the same word as `frontend-app` (the console,
`app.<domain>`) in conversation and in this directory's own file names — two unrelated services that
happened to share one word. `apply.sh --rollout app` is now `apply.sh --rollout server`;
`APP_ACTIVE_COLOR` in `.env` is now `SERVER_ACTIVE_COLOR`.

Left alone, on purpose: the public hostname `app.<domain>` still names `frontend-app`, unrelated to
this rename, and the two dated entries above this one keep the names (`app_blue`, `upstream app`)
that were actually typed during those spikes — a dated entry records what was measured at the time,
not what a later rename would have called it.

## 2026-08-31 — `--rollback` reads the tag from Docker itself, not from whoever is on call

Before this, undoing a bad rollout meant finding the previous release's tag by hand — in git tags
or the GHCR package page — and editing `.env`'s `*_IMAGE` before running `--rollout` again. That
is not just tedious under pressure; it is a live footgun: `server-blue` and `server-green` share
one `${BACKEND_IMAGE}` (`docker-compose.yml`'s own anchor), so forgetting to change the tag first
means the rollback's own `docker compose up -d` recreates the still-good colour with the still-bad
image, destroying the one thing that was supposed to save you.

The fix does not ask anyone to remember a tag at all. The colour a rollout demoted, if it has not
been retired yet, is still running the exact image it needs — `docker inspect --format
'{{.Config.Image}}'` on that container is ground truth, not a value that can drift out of sync
with reality the way a copy written into `.env` at deploy time could. `--rollback` reads it from
there, writes it into the same `.env` key `--rollout` would, and reuses `--rollout`'s own cutover
— extracted into `cutover_to_idle()` so the two commands cannot drift into two slightly different
cutovers over time.

**First draft was wrong, and worth recording why.** The first version of `do_rollback` called the
existing `do_rollout` unchanged, which for `server` unconditionally runs the old migrate image
first. That is not merely redundant — it is trusting an unverified thing: whether this project's
Flyway configuration tolerates a migrate run against a schema history that already carries
versions newer than anything on that older image's classpath ("future migrations") is not
something to assert from memory (`FlywayMigrations.kt` sets none of `ignoreFutureMigrations` /
`validateOnMigrate`, so it is whatever Flyway's own default happens to be). Rather than verify
that, the reasoning moved one level up: a rollback needs no migration at all, in either direction.
ADR-066 already requires every migration in a blue-green release to be additive-only precisely so
the demoted colour keeps working against the schema left behind — the same guarantee a rollback
leans on. `cutover_to_idle()` was pulled out of `do_rollout` with no migrate call in it, and
`do_rollback` calls only that.

**What happens once the idle colour is actually gone.** `--retire` now takes one extra,
best-effort step before it stops a colour: `docker inspect` its image and write it to
`*_IMAGE_PREVIOUS` in `.env` — the only moment that tag is still knowable at all, since Docker
drops it the instant the container is removed. `--rollback` falls back to that key when the idle
container no longer exists, at the honestly-stated cost of starting a fresh container instead of
cutting over to an already-warm one. If neither the container nor `*_IMAGE_PREVIOUS` has an
answer — nothing has ever been retired for this service yet, or `.env` was rebuilt from scratch —
`--rollback` says so and points at `git tag`/GHCR by name, rather than guessing.

**Not yet built, on purpose.** No automatic retire exists — nothing calls `--retire` on any
schedule, so both colours stay up until a human decides to free the memory. A timer-based
auto-retire was designed and compared against real precedent (AWS CodeDeploy's
`terminationWaitTimeInMinutes`, Argo Rollouts' `scaleDownDelaySeconds`, Netflix's own deliberately
manual Red/Black destroy) but not implemented — it needs a per-service, cancellable timer (not an
independent `sleep &` per rollout, which can let a stale timer from an earlier release destroy a
newer rollback window purely by outliving it), and that is its own, separate piece of work.

**Not verified live.** No Docker daemon was reachable while writing this (`apply.sh` runs only on
the server), so none of `--rollback`'s two paths — inspecting a still-running idle container,
falling back to `*_IMAGE_PREVIOUS` after a retire — has been exercised against real containers
yet. Treat this entry as a design record, not a confirmed one, until someone runs the same
rollout → rollback → retire → rollback sequence for real and reports back here.
