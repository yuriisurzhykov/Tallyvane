#!/usr/bin/env bash
# The one command a CI-only SSH key is allowed to run.
#
# `ops/provision/25-ci-key.sh` writes the `authorized_keys` line that names this script with
# `restrict,command="…"` — a forced command sshd runs no matter what the client asked to run,
# and `restrict` additionally strips port/X11/agent forwarding, a pty and ~/.ssh/rc, so a leaked
# key still can only ever end up here, never at an interactive shell.
#
# What the client actually asked for survives in $SSH_ORIGINAL_COMMAND, which this script reads
# as data, never as something to `eval`: anyone holding the key controls that string, and this is
# the second, independent guard behind the first — forced-command means sshd never runs it
# directly, this script means a value that gets this far still cannot reach a shell metacharacter
# unexamined. Two words, checked in order: a service name against a fixed list, an image
# reference against an allow-list of characters — because $BACKEND_IMAGE (etc.) ends up written
# into .env, which apply.sh later `source`s, and a shell metacharacter surviving into that file
# would run as this user the next time apply.sh starts.
#
#   ssh -i deploy-only-key deploy@host "app ghcr.io/owner/tallyvane-backend:v1.2.3"

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

original="${SSH_ORIGINAL_COMMAND:-}"
# shellcheck disable=SC2034  # $extra exists only to detect and reject a third word.
read -r service image_ref extra <<<"$original"

[ -z "$extra" ] || {
  echo "expected exactly two words (service, image reference), got: $original" >&2
  exit 1
}

case "$service" in
app) env_key=BACKEND_IMAGE ;;
frontend-web) env_key=FRONTEND_WEB_IMAGE ;;
frontend-app) env_key=FRONTEND_APP_IMAGE ;;
frontend-admin) env_key=FRONTEND_ADMIN_IMAGE ;;
*)
  echo "unknown service: '$service' (expected app, frontend-web, frontend-app, or frontend-admin)" >&2
  exit 1
  ;;
esac

# An allow-list, not a search for known-dangerous characters — the difference matters here:
# a deny-list has to anticipate every dangerous character, an allow-list only has to name the
# ones a real image reference ever legitimately contains.
case "$image_ref" in
*[!A-Za-z0-9._/:-]*)
  echo "rejected: image reference contains a character outside [A-Za-z0-9._/:-]: $image_ref" >&2
  exit 1
  ;;
esac
case "$image_ref" in
ghcr.io/*:*) ;;
*)
  echo "rejected: expected ghcr.io/<owner>/<repo>:<tag>, got: $image_ref" >&2
  exit 1
  ;;
esac

[ -f .env ] || {
  echo "no .env in $PWD — run the 'environment' provisioning step on this server" >&2
  exit 1
}
if grep -q "^${env_key}=" .env; then
  sed -i "s|^${env_key}=.*|${env_key}=${image_ref}|" .env
else
  printf '%s=%s\n' "$env_key" "$image_ref" >>.env
fi

exec bash apply.sh --rollout "$service"
