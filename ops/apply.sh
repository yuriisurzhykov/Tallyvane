#!/usr/bin/env bash
# Runs ON the server, in this directory. deploy.sh calls it after copying the tree here, and
# running it by hand does exactly the same thing — there is one path, not a scripted one and a
# manual one that drift apart.
#
# It used to be a heredoc piped into `ssh bash -s`, which put the script itself on standard
# input. `docker compose run` reads standard input, so it swallowed the rest of the script:
# validation passed, nothing started, and ssh reported success. A file cannot be eaten.
#
# Four modes, one file:
#
#   apply.sh                        the whole stack, from nothing — disaster recovery, first boot
#   apply.sh --rollout <service>    blue/green cutover for one service (CD plan §3)
#   apply.sh --retire <service>     stops the colour a rollout left running for its grace window
#   apply.sh --rollback <service>   cuts back to the colour --rollout just left running, no tag typed by hand
#
# <service> is one of: server, frontend-web, frontend-app, frontend-admin — the four services
# docker-compose.yml declares as blue/green pairs. `db`, `migrate`, `nginx` and `cloudflared`
# have no colour; nothing here rolls them out.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

[ -f .env ] || {
  echo "no .env in $PWD — run the 'environment' provisioning step on this server" >&2
  exit 1
}
set -a
# shellcheck disable=SC1091  # written on the server, absent from the repository by design.
. ./.env
set +a
: "${DOMAIN:?DOMAIN is missing from .env}"
: "${TUNNEL_ID:?TUNNEL_ID is missing from .env}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is missing from .env}"

command -v envsubst >/dev/null || {
  echo "envsubst is missing: apt-get install gettext-base" >&2
  exit 1
}

mode=full
service=""
case "${1:-}" in
--rollout)
  mode=rollout
  service="${2:?--rollout needs a service name: server, frontend-web, frontend-app, or frontend-admin}"
  ;;
--retire)
  mode=retire
  service="${2:?--retire needs a service name: server, frontend-web, frontend-app, or frontend-admin}"
  ;;
--rollback)
  mode=rollback
  service="${2:?--rollback needs a service name: server, frontend-web, frontend-app, or frontend-admin}"
  ;;
"") ;;
*)
  echo "usage: $0 [--rollout <service>|--retire <service>|--rollback <service>]" >&2
  exit 1
  ;;
esac

# ── blue/green helpers ───────────────────────────────────────────────────────────────────────
# Shared by every mode below, including the full-stack path — that path needs to know which
# colour is currently active just as much as a rollout does, or a disaster-recovery bring-up
# would silently revert every service to blue.

require_known_service() {
  case "$1" in
  server | frontend-web | frontend-app | frontend-admin) ;;
  *)
    echo "unknown service: $1 (expected server, frontend-web, frontend-app, or frontend-admin)" >&2
    return 1
    ;;
  esac
}

color_var_for() {
  case "$1" in
  server) echo SERVER_ACTIVE_COLOR ;;
  frontend-web) echo FRONTEND_WEB_ACTIVE_COLOR ;;
  frontend-app) echo FRONTEND_APP_ACTIVE_COLOR ;;
  frontend-admin) echo FRONTEND_ADMIN_ACTIVE_COLOR ;;
  esac
}

# The colour docker-compose.yml's own `${VAR:-blue}` would pick if this key were absent —
# repeated here rather than left to disagree with it by accident.
current_color() {
  local var
  var="$(color_var_for "$1")"
  printf '%s' "${!var:-blue}"
}

idle_color() {
  if [ "$1" = blue ]; then echo green; else echo blue; fi
}

# server -> BACKEND_IMAGE, and so on — the .env key --rollback overwrites, parallel to
# color_var_for() but for the image tag rather than the colour.
image_var_for() {
  case "$1" in
  server) echo BACKEND_IMAGE ;;
  frontend-web) echo FRONTEND_WEB_IMAGE ;;
  frontend-app) echo FRONTEND_APP_IMAGE ;;
  frontend-admin) echo FRONTEND_ADMIN_IMAGE ;;
  esac
}

# server -> BACKEND_IMAGE_PREVIOUS, and so on — do_retire's best-effort memory of the image
# a colour was running the moment it stopped existing, read back by do_rollback only when
# the colour it would rather inspect directly is already gone.
previous_image_var_for() {
  case "$1" in
  server) echo BACKEND_IMAGE_PREVIOUS ;;
  frontend-web) echo FRONTEND_WEB_IMAGE_PREVIOUS ;;
  frontend-app) echo FRONTEND_APP_IMAGE_PREVIOUS ;;
  frontend-admin) echo FRONTEND_ADMIN_IMAGE_PREVIOUS ;;
  esac
}

# Upserts KEY=VALUE into .env in place — idempotent, the same discipline provision/lib.sh's
# write_file applies to host files, here applied to the one file deploy.sh never syncs and
# apply.sh is consequently the only writer of. Exported immediately too, so a caller that reads
# $SERVER_ACTIVE_COLOR right after calling this sees the new value without re-sourcing the file.
set_env_var() {
  local key="$1" value="$2"
  if grep -q "^${key}=" .env; then
    sed -i "s|^${key}=.*|${key}=${value}|" .env
  else
    printf '%s=%s\n' "$key" "$value" >>.env
  fi
  export "$key=$value"
}

# Regenerates conf.d/00-common.conf inside the RUNNING nginx container from its own template and
# reloads — the same envsubst the image's entrypoint runs at startup, invoked again by hand so a
# colour switch reaches nginx without restarting it. A restart would drop every hostname for the
# couple of seconds it takes to come back, not only the one service being rolled out (measured,
# ops/README.md 2026-08-28) — the whole reason this function exists instead of a plain
# `docker compose up -d nginx`.
#
# Every PRIMARY/SECONDARY pair is passed on every call, even for the three services not being
# touched — envsubst has no notion of "leave this one as it was," and an unsubstituted
# placeholder is exactly the silent failure the DOMAIN entry in ops/README.md already documents
# once, for a different variable.
render_common_conf() {
  # Named explicitly, the same reasoning as the cloudflared envsubst call below: the entrypoint's
  # own $NGINX_ENVSUBST_FILTER is a regex it feeds to `awk` to build its own ${VAR} list from
  # whatever happens to be in the environment (read from the image directly — its
  # 20-envsubst-on-templates.sh — rather than assumed), not a string envsubst itself understands.
  # Passing the filter straight to envsubst would ask it to substitute a variable named
  # literally `^TALLYVANE_`, find none, and silently substitute nothing at all.
  # shellcheck disable=SC2016  # envsubst wants the literal names, not their values.
  docker compose exec -T \
    -e TALLYVANE_SERVER_PRIMARY="$1" -e TALLYVANE_SERVER_SECONDARY="$2" \
    -e TALLYVANE_FRONTEND_WEB_PRIMARY="$3" -e TALLYVANE_FRONTEND_WEB_SECONDARY="$4" \
    -e TALLYVANE_FRONTEND_APP_PRIMARY="$5" -e TALLYVANE_FRONTEND_APP_SECONDARY="$6" \
    -e TALLYVANE_FRONTEND_ADMIN_PRIMARY="$7" -e TALLYVANE_FRONTEND_ADMIN_SECONDARY="$8" \
    nginx sh -c '
      set -e
      vars="\${TALLYVANE_SERVER_PRIMARY} \${TALLYVANE_SERVER_SECONDARY}"
      vars="$vars \${TALLYVANE_FRONTEND_WEB_PRIMARY} \${TALLYVANE_FRONTEND_WEB_SECONDARY}"
      vars="$vars \${TALLYVANE_FRONTEND_APP_PRIMARY} \${TALLYVANE_FRONTEND_APP_SECONDARY}"
      vars="$vars \${TALLYVANE_FRONTEND_ADMIN_PRIMARY} \${TALLYVANE_FRONTEND_ADMIN_SECONDARY}"
      envsubst "$vars" < /etc/nginx/templates/00-common.conf.template > /etc/nginx/conf.d/00-common.conf.new
      mv /etc/nginx/conf.d/00-common.conf.new /etc/nginx/conf.d/00-common.conf
      nginx -t
      nginx -s reload
    '
}

# Fills every service's PRIMARY/SECONDARY from its current colour (both the same — steady
# state), then applies one override for at most one service. Called with no override to simply
# reassert steady state (--retire), and with one during a rollout's two cutover reloads.
reload_upstreams() {
  local override_service="${1:-}" override_primary="${2:-}" override_secondary="${3:-}"
  local server_p server_s web_p web_s appfe_p appfe_s admin_p admin_s

  server_p="server-$(current_color server)"
  server_s="$server_p"
  web_p="frontend-web-$(current_color frontend-web)"
  web_s="$web_p"
  appfe_p="frontend-app-$(current_color frontend-app)"
  appfe_s="$appfe_p"
  admin_p="frontend-admin-$(current_color frontend-admin)"
  admin_s="$admin_p"

  case "$override_service" in
  server)
    server_p="$override_primary"
    server_s="$override_secondary"
    ;;
  frontend-web)
    web_p="$override_primary"
    web_s="$override_secondary"
    ;;
  frontend-app)
    appfe_p="$override_primary"
    appfe_s="$override_secondary"
    ;;
  frontend-admin)
    admin_p="$override_primary"
    admin_s="$override_secondary"
    ;;
  "") ;;
  esac

  render_common_conf "$server_p" "$server_s" "$web_p" "$web_s" "$appfe_p" "$appfe_s" "$admin_p" "$admin_s"
}

wait_healthy_service() {
  local container="$1" tries="${2:-30}" id health=unknown
  id="$(docker compose ps -q "$container")"
  [ -n "$id" ] || {
    echo "$container has no container to wait on" >&2
    return 1
  }
  for _ in $(seq 1 "$tries"); do
    health="$(docker inspect --format '{{.State.Health.Status}}' "$id" 2>/dev/null || echo unknown)"
    [ "$health" = healthy ] && return 0
    sleep 2
  done
  echo "$container is $health after $((tries * 2))s" >&2
  docker compose logs --tail 40 "$container" >&2
  return 1
}

# server only: polls /api/v1/health/ready directly on the container, bypassing nginx entirely. The
# Docker healthcheck (wait_healthy_service, used for the frontends) only calls /health/live,
# which by design consults nothing (ADR-063) — it proves the process started, not that it may
# serve traffic. /health/ready needs no service token; only the detailed breakdown does.
wait_ready() {
  local container="$1" tries="${2:-30}"
  for _ in $(seq 1 "$tries"); do
    if docker compose exec -T "$container" \
      curl --fail --silent --output /dev/null http://127.0.0.1:8080/api/v1/health/ready; then
      return 0
    fi
    sleep 2
  done
  echo "$container never answered 200 on /api/v1/health/ready after $((tries * 2))s" >&2
  docker compose logs --tail 40 "$container" >&2
  return 1
}

# One hostname, one check, reusing deploy.sh's own reasoning: a status code alone cannot tell two
# hostnames apart if something is serving the wrong one, so each check reads a marker from the
# body too — except admin, checked the other way round (a challenge, and the page absent), for
# the same reason deploy.sh's own admin check is.
smoke_check_service() {
  local service="$1" host status body headers
  case "$service" in
  server)
    host="app.${DOMAIN}"
    status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 20 "https://${host}/api/v1/health/ready" || true)"
    [ "$status" = 200 ] || {
      echo "https://${host}/api/v1/health/ready -> $status, expected 200" >&2
      return 1
    }
    ;;
  frontend-web)
    host="${DOMAIN}"
    body="$(curl --silent --max-time 20 "https://${host}/" || true)"
    printf '%s' "$body" | grep -q 'content="site"' || {
      echo "https://${host}/ did not carry surface=site" >&2
      return 1
    }
    ;;
  frontend-app)
    host="app.${DOMAIN}"
    body="$(curl --silent --max-time 20 "https://${host}/" || true)"
    printf '%s' "$body" | grep -q 'content="app"' || {
      echo "https://${host}/ did not carry surface=app" >&2
      return 1
    }
    ;;
  frontend-admin)
    host="admin.${DOMAIN}"
    headers="$(curl --silent --show-error --head --max-time 20 "https://${host}/" || true)"
    printf '%s' "$headers" | grep -qi 'www-authenticate:.*Cloudflare-Access' ||
      printf '%s' "$headers" | grep -qi 'location:.*cloudflareaccess\.com' || {
      echo "https://${host}/ answered without a Cloudflare Access challenge" >&2
      return 1
    }
    ;;
  esac
  echo "ok: https://${host}/"
}

# ── shared by --rollout and --rollback ───────────────────────────────────────────────────────

# Starts the idle colour on whatever image .env currently names for this service, waits for
# it, cuts nginx over in two reloads, and smoke-checks the public hostname. Deliberately runs
# no migration of any kind — do_rollout is the only caller that decides to, and do_rollback
# must never inherit that decision by calling this instead of duplicating it (see do_rollback's
# own comment for why running the old migrate image on a rollback would be wrong, not just
# unnecessary).
cutover_to_idle() {
  local service="$1"
  local active idle active_container idle_container
  active="$(current_color "$service")"
  idle="$(idle_color "$active")"
  active_container="${service}-${active}"
  idle_container="${service}-${idle}"

  echo "-- starting $idle_container"
  docker compose up -d "$idle_container"

  echo "-- waiting for $idle_container"
  if [ "$service" = server ]; then
    wait_ready "$idle_container"
  else
    wait_healthy_service "$idle_container"
  fi

  echo "-- adding $idle_container to nginx, marked down, and reloading"
  reload_upstreams "$service" "$active_container" "$idle_container"

  echo "-- cutover: $idle_container becomes primary"
  reload_upstreams "$service" "$idle_container" "$active_container"
  set_env_var "$(color_var_for "$service")" "$idle"

  echo "-- smoke-checking the public hostname"
  smoke_check_service "$service"

  echo "-- $active_container stays up, marked down, for instant rollback or: $0 --retire $service"
}

# ── --rollout ─────────────────────────────────────────────────────────────────────────────────

do_rollout() {
  local service="$1"
  require_known_service "$service"

  echo "-- rolling out $service: $(current_color "$service") (active) -> $(idle_color "$(current_color "$service")") (idle)"

  if [ "$service" = server ]; then
    # Before the idle colour starts, not after — a schema the new colour needs but the old
    # colour has not been told to tolerate yet is exactly what ADR-066 exists to rule out, and
    # this is the one moment that matters. Explicit rather than left to `depends_on`: a scoped
    # `up -d server-<colour>` does not reliably re-run a one-shot dependency that already exited
    # successfully once, for a previous release.
    echo "-- applying migrations for the new image"
    docker compose run --rm migrate
  fi

  cutover_to_idle "$service"
}

# ── --retire ──────────────────────────────────────────────────────────────────────────────────

do_retire() {
  local service="$1"
  require_known_service "$service"

  local active old_container old_id old_image
  active="$(current_color "$service")"
  old_container="${service}-$(idle_color "$active")"

  # Best-effort memory of what old_container was running, written before it is removed and
  # Docker forgets — the only thing that lets --rollback still work once this colour is gone,
  # at the cost of a fresh container start instead of an instant one. Not required for retire
  # itself: a container already gone by some other means just means nothing new to remember,
  # not a reason to fail the retire.
  old_id="$(docker compose ps -aq "$old_container" 2>/dev/null || true)"
  if [ -n "$old_id" ]; then
    old_image="$(docker inspect --format '{{.Config.Image}}' "$old_id" 2>/dev/null || true)"
    [ -n "$old_image" ] && set_env_var "$(previous_image_var_for "$service")" "$old_image"
  fi

  # Removed from nginx's config before the container stops, never after: nginx resolves every
  # declared upstream server at load time, `down` or not, so a reload that still names a
  # container already stopped fails outright — for every hostname, not only this service's
  # (measured, ops/README.md 2026-08-28). This call carries no override, which is exactly what
  # asserting plain steady state means: both PRIMARY and SECONDARY mirror the current colour.
  echo "-- removing $old_container from nginx before stopping it"
  reload_upstreams

  echo "-- stopping $old_container"
  docker compose stop "$old_container"
  docker compose rm -f "$old_container"
}

# ── --rollback ────────────────────────────────────────────────────────────────────────────────

# Cuts back to whichever colour is currently idle, reading the image tag it needs from
# somewhere that cannot be misremembered instead of asking a human to type it:
#
#   1. If the idle container still exists (do_retire has not run since the last --rollout),
#      its own `docker inspect` is the source of truth — always exactly right, because it is
#      the same image that colour has been running the whole time, not a value copied into
#      .env and hoping the two never drift apart.
#   2. If it has already been retired, fall back to *_IMAGE_PREVIOUS — do_retire's own
#      best-effort snapshot, taken in the one moment that image tag was still knowable at all.
#      A fresh container starts from that image instead of an already-warm one, so this path
#      is the speed of an ordinary rollout, not an instant cutover.
#
# Deliberately runs no migration, forward or backward. ADR-066 already requires every
# migration a blue-green release ships to be additive-only precisely so the colour it demotes
# keeps working against the schema left behind — which is exactly the guarantee a rollback
# relies on, and exactly why it does not also need to run the demoted colour's own migrate
# image against a schema a newer one may since have advanced further: nothing needs migrating
# in either direction. Running that older migrate image anyway would only add a dependency on
# how it happens to behave against migrations it has never seen — unverified, and not needed
# for anything this command does.
do_rollback() {
  local service="$1"
  require_known_service "$service"

  local idle idle_container image_key fallback_key container_id previous_image
  idle="$(idle_color "$(current_color "$service")")"
  idle_container="${service}-${idle}"
  image_key="$(image_var_for "$service")"
  fallback_key="$(previous_image_var_for "$service")"

  container_id="$(docker compose ps -aq "$idle_container")"
  if [ -n "$container_id" ]; then
    previous_image="$(docker inspect --format '{{.Config.Image}}' "$container_id")"
    [ -n "$previous_image" ] || {
      echo "docker inspect returned no image for $idle_container — refusing to touch .env" >&2
      return 1
    }
    echo "-- rollback $service: $image_key -> $previous_image (read from the still-running $idle_container)"
  else
    previous_image="${!fallback_key:-}"
    [ -n "$previous_image" ] || {
      echo "$idle_container does not exist and $fallback_key is not set in .env" >&2
      echo "look up the previous tag yourself (git tag -l '${service}-v*' or the GHCR package page) and use --rollout" >&2
      return 1
    }
    echo "-- rollback $service: $image_key -> $previous_image (from $fallback_key — $idle_container was already retired, this starts it fresh rather than cutting over instantly)"
  fi

  set_env_var "$image_key" "$previous_image"
  cutover_to_idle "$service"
}

case "$mode" in
rollout)
  do_rollout "$service"
  echo "DOMAIN=$DOMAIN"
  exit 0
  ;;
retire)
  do_retire "$service"
  exit 0
  ;;
rollback)
  do_rollback "$service"
  echo "DOMAIN=$DOMAIN"
  exit 0
  ;;
esac

# ── full stack, from nothing ─────────────────────────────────────────────────────────────────
# Disaster recovery and first boot. A rollout switch has nothing to switch on an empty machine,
# so this path stays — it starts whichever colour .env already says is active for each service,
# never both: a bare `docker compose up -d` cannot start the other colour by accident regardless,
# because every blue/green service carries `profiles: [blue-green]` and is invisible to a command
# that does not name it explicitly (docker-compose.yml's own comment on the anchors has the
# measurement).

# Naming the two variables explicitly, rather than letting envsubst substitute everything it
# finds: a bare envsubst would also eat any other ${...} the template ever grows.
# shellcheck disable=SC2016  # envsubst wants the literal names, not their values.
envsubst '${TUNNEL_ID} ${DOMAIN}' <cloudflared/config.yml.template >cloudflared/config.yml

# Docker creates a DIRECTORY for a bind mount whose host path is missing, and cloudflared then
# fails with something that reads like a credentials format problem. Checking here turns that
# into one sentence.
credentials="${TUNNEL_CREDENTIALS:-/srv/secrets/tallyvane/tunnel-credentials.json}"
[ -f "$credentials" ] || {
  echo "$credentials is not a file — run the 'tunnel' provisioning step on this server" >&2
  exit 1
}

server_color="$(current_color server)"
web_color="$(current_color frontend-web)"
appfe_color="$(current_color frontend-app)"
admin_color="$(current_color frontend-admin)"

# The backend chain and all three frontends first, because the check below needs every one
# of them: nginx names each colour's container in an upstream group, and a literal name there is
# resolved when nginx starts. With any one of those containers missing from the network, the
# check fails on the first deploy that introduces it. Compose waits for migrations to finish
# before starting the server; the three frontends have no such dependency on each other or on the
# backend.
echo "-- starting the backend and the frontends (blue/green: $server_color/$web_color/$appfe_color/$admin_color)"
docker compose up -d db migrate \
  "server-${server_color}" "frontend-web-${web_color}" "frontend-app-${appfe_color}" "frontend-admin-${admin_color}"

# Through the image's real entrypoint, so the templates are substituted first: checking the
# template directory would prove nothing, because the file nginx reads does not exist until that
# entrypoint writes it. -T and /dev/null keep this command away from standard input whether or
# not there is a terminal on the other end.
echo "-- validating the nginx configuration"
docker compose run --rm --no-deps -T nginx nginx -t </dev/null

echo "-- starting the edge"
docker compose up -d --remove-orphans nginx cloudflared
docker compose ps

echo "-- waiting for nginx to report healthy"
container="$(docker compose ps -q nginx)"
health=unknown
for _ in $(seq 1 30); do
  health="$(docker inspect --format '{{.State.Health.Status}}' "$container")"
  [ "$health" = healthy ] && break
  sleep 2
done
[ "$health" = healthy ] || {
  echo "nginx is $health" >&2
  docker compose logs --tail 40 nginx >&2
  exit 1
}
echo "nginx healthy"

# nginx resolves the names in its upstream groups once, at startup, and keeps the addresses.
# A recreated container can come back on a different one, and nginx would go on using the old
# address until something told it otherwise. `-s reload` re-reads the configuration and
# re-resolves: new worker processes start on the new configuration while the old ones finish the
# requests they already accepted, so nothing is dropped.
docker compose exec -T nginx nginx -s reload
echo "-- nginx reloaded, upstream addresses re-resolved"

# Read by deploy.sh to know which hostnames to ask for. Keep it last.
echo "DOMAIN=$DOMAIN"
