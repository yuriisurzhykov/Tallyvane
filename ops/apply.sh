#!/usr/bin/env bash
# Runs ON the server, in this directory. deploy.sh calls it after copying the tree here, and
# running it by hand does exactly the same thing — there is one path, not a scripted one and a
# manual one that drift apart.
#
# It used to be a heredoc piped into `ssh bash -s`, which put the script itself on standard
# input. `docker compose run` reads standard input, so it swallowed the rest of the script:
# validation passed, nothing started, and ssh reported success. A file cannot be eaten.

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

# Docker creates a DIRECTORY for a bind mount whose host path is missing, and cloudflared then
# fails with something that reads like a credentials format problem. Checking here turns that
# into one sentence.
credentials="${TUNNEL_CREDENTIALS:-/srv/secrets/tallyvane/tunnel-credentials.json}"
[ -f "$credentials" ] || {
  echo "$credentials is not a file — run the 'tunnel' provisioning step on this server" >&2
  exit 1
}

# Naming the two variables explicitly, rather than letting envsubst substitute everything it
# finds: a bare envsubst would also eat any other ${...} the template ever grows.
# shellcheck disable=SC2016  # envsubst wants the literal names, not their values.
envsubst '${TUNNEL_ID} ${DOMAIN}' <cloudflared/config.yml.template >cloudflared/config.yml

# The backend chain first, because the check below needs it: nginx names `app` in an upstream
# group, and a literal name there is resolved when nginx starts. With no such container in the
# network the check fails on the first deploy that introduces it — the check blocking the deploy
# it exists to protect. Compose waits for migrations to finish before starting the server.
echo "-- starting the backend"
docker compose up -d db migrate app

# Through the image's real entrypoint, so the templates are substituted first: checking the
# template directory would prove nothing, because the file nginx reads does not exist until that
# entrypoint writes it. -T and /dev/null keep this command away from standard input whether or
# not there is a terminal on the other end.
echo "-- validating the nginx configuration"
docker compose run --rm --no-deps -T nginx nginx -t </dev/null

echo "-- starting the edge"
docker compose up -d --remove-orphans
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
