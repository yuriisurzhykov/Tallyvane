#!/usr/bin/env bash
# Base packages every later step assumes, and a look at what this machine actually is.
#
# `gettext-base` is here for one reason that is easy to miss: it provides `envsubst`, which
# ops/deploy.sh uses to turn cloudflared's template into its configuration. Without it the
# deploy fails at the last step, after the files have already been copied.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib.sh"

require_root
step "Preflight"

if [ -r /etc/os-release ]; then
  # shellcheck disable=SC1091
  . /etc/os-release
  info "host: ${PRETTY_NAME:-unknown}, kernel $(uname -r), $(nproc) cpu, $(free -m | awk '/^Mem:/ {print $2}') MB"
  case "${ID:-}" in
  ubuntu | debian) ok "apt-based distribution" ;;
  *) warn "these scripts assume apt; ${ID:-unknown} is untested" ;;
  esac
else
  warn "/etc/os-release is missing; cannot tell what this machine runs"
fi

info "refreshing package lists"
DEBIAN_FRONTEND=noninteractive apt-get update --quiet

apt_install ca-certificates curl gnupg rsync gettext-base dnsutils jq

ok "preflight complete"
