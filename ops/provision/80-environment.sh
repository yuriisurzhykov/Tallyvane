#!/usr/bin/env bash
# The .env compose reads, on the server and only on the server.
#
# Keys are only ever added, never rewritten. A script that overwrites this file is a script
# that one day replaces a working database password with a fresh one, and PostgreSQL keeps
# the old one — the volume was initialised with it.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib.sh"

require_root
step "Environment"

env_file="$TALLYVANE_APP_DIR/.env"
[ -d "$TALLYVANE_APP_DIR" ] || die "$TALLYVANE_APP_DIR is missing — run the 'directories' step first"
[ -f "$env_file" ] || install -m 600 -o "$TALLYVANE_USER" -g "$TALLYVANE_USER" /dev/null "$env_file"

has_key() { grep -qE "^$1=." "$env_file"; }
add_key() {
  printf '%s=%s\n' "$1" "$2" >>"$env_file"
  ok "$1 set"
}

if has_key DOMAIN; then
  ok "DOMAIN already set to $(grep -E '^DOMAIN=' "$env_file" | tail -n 1 | cut -d= -f2-)"
else
  while true; do
    printf '\n'
    read -r -p '  Zone in Cloudflare, without a scheme or a subdomain (e.g. example.com): ' domain </dev/tty
    if printf '%s' "$domain" | grep -qE '^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$'; then
      add_key DOMAIN "$domain"
      break
    fi
    warn "that does not look like a domain — no https://, no trailing slash, no subdomain"
  done
fi

if has_key POSTGRES_PASSWORD; then
  ok "POSTGRES_PASSWORD already set (left alone: the database volume was initialised with it)"
else
  # Hexadecimal rather than base64: 32 bytes of entropy either way, but hex cannot contain a
  # character that compose's .env parser or a shell would treat as syntax.
  add_key POSTGRES_PASSWORD "$(openssl rand -hex 32)"
fi

# Opens the detailed health report. The application refuses to start on anything shorter than 40
# characters; 32 bytes as hex is 64, so this cannot accidentally fall under the floor.
if has_key TALLYVANE_HEALTH_TOKEN; then
  ok "TALLYVANE_HEALTH_TOKEN already set"
else
  add_key TALLYVANE_HEALTH_TOKEN "$(openssl rand -hex 32)"
fi

chown "$TALLYVANE_USER:$TALLYVANE_USER" "$env_file"
chmod 600 "$env_file"

# TUNNEL_ID is not asked for here: the 'tunnel' step knows it only once the tunnel exists.
has_key TUNNEL_ID || info "TUNNEL_ID is still missing; the 'tunnel' step fills it in"

ok "$(stat -c '%A %U:%G %n' "$env_file")"
