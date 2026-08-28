#!/usr/bin/env bash
# Refuse every inbound port except SSH.
#
# Nothing else needs to be open: cloudflared reaches Cloudflare with an outbound connection,
# and requests arrive back through it. Ports 80 and 443 stay closed, which also means nobody
# can reach this machine directly and bypass Cloudflare's filtering.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib.sh"

require_root
step "Firewall"

apt_install ufw

ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ok "default: deny inbound, allow outbound"

# `limit` rather than `allow`: ufw drops connections from an address that opens more than six
# in thirty seconds, which costs a brute-force attempt most of its rate for free.
ufw limit OpenSSH >/dev/null
ufw logging low >/dev/null

# --force because `ufw enable` otherwise asks for confirmation, and the warning it asks about
# — that enabling may drop the current SSH session — is exactly what the rule above prevents.
ufw --force enable >/dev/null

ufw status verbose | grep -q '^Status: active' || die "ufw is not active"
ufw status | grep -q '22/tcp.*LIMIT' || die "the SSH rule is missing; refusing to leave the firewall in this state"
ok "active, SSH rate-limited, everything else refused"
ufw status verbose | sed 's/^/     /'

manual_notice "the provider's own firewall" "$(
  cat <<'GUIDE'
  IONOS filters traffic before it reaches this machine, in their control panel, and that
  layer is invisible from here. Confirm there that inbound TCP 22 is allowed and nothing
  else is.

  From your own machine, 22 should answer and 80 should not:

      nc -z -w3 <this-server> 22   # expected: succeeds
      nc -z -w3 <this-server> 80   # expected: fails

  This script cannot check either of those, which is why it is asking instead of claiming.
GUIDE
)"
