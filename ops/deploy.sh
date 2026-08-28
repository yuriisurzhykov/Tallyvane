#!/usr/bin/env bash
# Copies this directory to the server and brings the composition up. Run it from a clone, on
# your own machine — not on the server, which holds no source code by design.
#
#   ops/deploy.sh deploy@203.0.113.10
#   TALLYVANE_SSH=tallyvane ops/deploy.sh          # an SSH host alias
#
# The sync deletes anything on the server that is not in the repository, which is how a route
# removed here stops being served there. Two things are therefore kept out of its reach: .env,
# excluded below, and the tunnel credentials, which live outside this tree entirely.

set -euo pipefail

OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${1:-${TALLYVANE_SSH:-}}"
REMOTE_DIR="${TALLYVANE_APP_DIR:-/srv/apps/tallyvane}"

info() { printf '     %s\n' "$*"; }
ok() { printf '  ok %s\n' "$*"; }
step() { printf '\n== %s\n' "$*"; }
die() {
  printf '\nFAILED: %s\n' "$*" >&2
  exit 1
}

[ -n "$TARGET" ] || die "usage: $0 user@host   (or set TALLYVANE_SSH)"
for tool in rsync ssh curl; do
  command -v "$tool" >/dev/null || die "$tool is not installed on this machine"
done
[ -f "$OPS_DIR/docker-compose.yml" ] || die "$OPS_DIR does not look like the ops directory"

step "Copying $OPS_DIR to $TARGET:$REMOTE_DIR"
rsync --archive --compress --delete --human-readable --itemize-changes \
  --exclude '.env' \
  --exclude 'cloudflared/config.yml' \
  "$OPS_DIR/" "$TARGET:$REMOTE_DIR/"

step "Applying on the server"
# apply.sh is a file in the tree just copied, invoked by path. It is deliberately not piped
# into `bash -s`: that puts the script on standard input, and `docker compose run` inside it
# reads standard input and swallowed the remainder — validation passed, nothing started, and
# ssh reported success.
remote_log="$(mktemp)"
trap 'rm -f "$remote_log"' EXIT
# shellcheck disable=SC2029  # REMOTE_DIR is meant to expand here, on this side.
ssh "$TARGET" "bash $REMOTE_DIR/apply.sh" | tee "$remote_log"

# The domain comes back in apply.sh's last line rather than from a second ssh call. One round
# trip fewer, and no question about which side of the connection expands what.
domain="$(sed -n 's/^DOMAIN=//p' "$remote_log" | tail -n 1)"
[ -n "$domain" ] || die "the server did not report its DOMAIN"

step "Smoke check against $domain, from this machine"
failures=0

# Asking over the public internet, and reading the marker in the page rather than just the
# status code. Two 200s prove the tunnel reaches nginx; only the markers prove the hostnames
# reach different servers — three 200s once came from three hostnames all being answered by
# the same built-in nginx welcome page.
for pair in ":site" "app.:app"; do
  prefix="${pair%%:*}"
  expected="${pair##*:}"
  host="${prefix}${domain}"
  body="$(curl --silent --show-error --location --max-time 20 "https://$host/" || true)"
  status="$(curl --silent --output /dev/null --write-out '%{http_code}' --location --max-time 20 "https://$host/" || true)"
  if [ "$status" = 200 ] && printf '%s' "$body" | grep -q "content=\"$expected\""; then
    ok "https://$host -> 200, surface=$expected"
  else
    printf '  !! https://%s -> status=%s, expected surface=%s\n' "$host" "$status" "$expected" >&2
    failures=$((failures + 1))
  fi
done

# The admin hostname is asserted the other way round: Cloudflare Access answers before the
# request ever reaches the tunnel, so a 200 with the page in it would mean the guard is gone.
#
# Two signals are accepted because either alone is brittle. `www-authenticate:
# Cloudflare-Access` states outright that the resource is protected; a redirect to
# `cloudflareaccess.com` says the same thing in the older shape. Asserting on the redirect
# target alone would also tie this check to the team name, which is configuration.
#
# The second half of the condition carries as much weight as the first. Without a session the
# page itself must not come back — a check that only looked for a challenge would still pass
# if Access were removed and something else redirected.
admin_host="admin.$domain"
admin_headers="$(curl --silent --show-error --head --max-time 20 "https://$admin_host/" || true)"
admin_body="$(curl --silent --max-time 20 "https://$admin_host/" || true)"
guarded=no
printf '%s' "$admin_headers" | grep -qi 'www-authenticate:.*Cloudflare-Access' && guarded=yes
printf '%s' "$admin_headers" | grep -qi 'location:.*cloudflareaccess\.com' && guarded=yes

if [ "$guarded" = yes ] && ! printf '%s' "$admin_body" | grep -q 'content="admin"'; then
  ok "https://$admin_host -> Cloudflare Access challenge, application not served"
else
  printf '  !! https://%s answered without an Access challenge, or served the application to an unauthenticated request\n' "$admin_host" >&2
  failures=$((failures + 1))
fi

[ "$failures" -eq 0 ] || die "$failures of 3 hostnames answered wrongly"
step "Deployed"
