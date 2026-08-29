#!/usr/bin/env bash
# The tunnel: the only way a request from the internet reaches this machine.
#
# cloudflared is installed on the host even though the tunnel itself runs in a container. The
# container runs it; these commands administer it — a browser login, creating the tunnel,
# pointing DNS at it — and they happen once, by hand, not on every deploy.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib.sh"

require_root
step "Cloudflare tunnel"

TUNNEL_NAME="${TALLYVANE_TUNNEL_NAME:-tallyvane-vps}"
env_file="$TALLYVANE_APP_DIR/.env"
credentials="$TALLYVANE_SECRETS_DIR/tunnel-credentials.json"

[ -f "$env_file" ] || die "$env_file is missing — run the 'environment' step first"
domain="$(grep -E '^DOMAIN=' "$env_file" | tail -n 1 | cut -d= -f2-)"
[ -n "$domain" ] || die "DOMAIN is not set in $env_file"

# shellcheck disable=SC1091
. /etc/os-release
codename="${UBUNTU_CODENAME:-${VERSION_CODENAME:-}}"

if [ ! -s /usr/share/keyrings/cloudflare-main.gpg ]; then
  info "fetching Cloudflare's signing key"
  curl --fail --silent --show-error --location https://pkg.cloudflare.com/cloudflare-main.gpg \
    -o /usr/share/keyrings/cloudflare-main.gpg
  chmod a+r /usr/share/keyrings/cloudflare-main.gpg
fi

write_file /etc/apt/sources.list.d/cloudflared.list 644 <<CONFIG
# Managed by the 'tunnel' step in ops/provision/.
deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared $codename main
CONFIG

if [ "$FILE_CHANGED" = yes ]; then
  DEBIAN_FRONTEND=noninteractive apt-get update --quiet ||
    die "apt could not read Cloudflare's repository for '$codename'; install the .deb from https://github.com/cloudflare/cloudflared/releases instead"
fi
apt_install cloudflared

# The login writes an account certificate into the home directory of whoever ran it. This may
# already have been done as the deploy user rather than under sudo, so both are accepted.
ORIGIN_CERT=""
find_origin_cert() {
  local candidate
  for candidate in "/home/$TALLYVANE_USER/.cloudflared/cert.pem" /root/.cloudflared/cert.pem; do
    if [ -s "$candidate" ]; then
      ORIGIN_CERT="$candidate"
      return 0
    fi
  done
  return 1
}

manual_step "Cloudflare login" find_origin_cert "$(
  cat <<'GUIDE'
  Run this and open the URL it prints in a browser where you are signed in to Cloudflare,
  then pick the zone:

      cloudflared tunnel login

  It writes an account certificate into ~/.cloudflared/cert.pem, which is what this script
  looks for.
GUIDE
)"
find_origin_cert || die "no cert.pem after the login step"
ok "account certificate: $ORIGIN_CERT"

cf() { cloudflared --origincert "$ORIGIN_CERT" "$@"; }

tunnel_id_of() {
  cf tunnel list --output json |
    jq -r --arg name "$TUNNEL_NAME" '.[] | select(.name == $name) | .id' |
    head -n 1
}
tunnel_exists() {
  local id
  id="$(tunnel_id_of)" || {
    echo "cloudflared tunnel list failed; its output is above" >&2
    return 1
  }
  [ -n "$id" ] || {
    echo "no tunnel named '$TUNNEL_NAME' in: $(cf tunnel list --output json | jq -r '[.[].name] | join(", ")')" >&2
    return 1
  }
}

manual_step "tunnel '$TUNNEL_NAME' exists" tunnel_exists "$(
  cat <<GUIDE
  Create it:

      cloudflared tunnel create $TUNNEL_NAME

  If it already exists under a different name, either rename it in the Cloudflare dashboard
  or re-run this script with TALLYVANE_TUNNEL_NAME set to that name.
GUIDE
)"

tunnel_id="$(tunnel_id_of)"
[ -n "$tunnel_id" ] || die "cannot read the tunnel id"
ok "tunnel $TUNNEL_NAME is $tunnel_id"

if grep -qE '^TUNNEL_ID=.' "$env_file"; then
  ok "TUNNEL_ID already in $env_file"
else
  printf 'TUNNEL_ID=%s\n' "$tunnel_id" >>"$env_file"
  ok "TUNNEL_ID written to $env_file"
fi

# The credentials file is the actual secret — the tunnel id is not. It goes outside the tree
# deploy.sh syncs, owned by the id the cloudflared image runs as and readable by nobody else.
# That ownership is what lets the container keep its own non-root user.
source_credentials="$(dirname "$ORIGIN_CERT")/$tunnel_id.json"
if [ -s "$credentials" ]; then
  ok "credentials already in place"
elif [ -s "$source_credentials" ]; then
  install -m 400 -o "$TALLYVANE_TUNNEL_UID" -g "$TALLYVANE_TUNNEL_UID" "$source_credentials" "$credentials"
  ok "credentials installed from $source_credentials"
else
  die "cannot find $source_credentials — recreate the tunnel, or copy its credentials file to $credentials by hand"
fi

[ "$(stat -c '%u %a' "$credentials")" = "$TALLYVANE_TUNNEL_UID 400" ] ||
  die "$credentials must be owned by $TALLYVANE_TUNNEL_UID with mode 400, and is $(stat -c '%u %a' "$credentials")"
ok "$(stat -c '%u:%g %a %n' "$credentials")"

# DNS records point the three hostnames at the tunnel. Deliberately not treated as proof of
# anything: a record can exist, resolve, and still be wrong. What proves this chain is the
# smoke check at the end of ops/deploy.sh, which asks for a page and reads what comes back.
for hostname in "$domain" "app.$domain" "admin.$domain"; do
  if route_error="$(cf tunnel route dns "$TUNNEL_NAME" "$hostname" 2>&1)"; then
    ok "route: $hostname"
  else
    # Showing what cloudflared actually said, rather than assuming which failure this is.
    # "Already exists" for a record the dashboard shows as Tunnel pointing at this tunnel
    # means there is nothing to do; the same message for an imported A record means the
    # opposite, and only the message and the dashboard can tell them apart.
    manual_notice "DNS record for $hostname" "$(
      cat <<GUIDE
  cloudflared said:

$(printf '%s\n' "$route_error" | sed 's/^/      /')

  If the dashboard already shows $hostname as type Tunnel pointing at $TUNNEL_NAME, this is
  nothing: the record is correct and cloudflared simply refuses to touch it again.

  If it shows an A, AAAA or CNAME record instead, that is a leftover Cloudflare imported when
  the zone was added, and requests go straight to a port the firewall refuses — which answers
  522. Delete that one record in DNS -> Records and run:

      cloudflared tunnel route dns $TUNNEL_NAME $hostname

  Delete nothing else. MX and TXT are the domain's mail — SPF, DMARC, autodiscover — and
  removing them stops mail rather than breaking a web page.
GUIDE
    )"
  fi
done

ok "tunnel ready; deploy with ops/deploy.sh from a clone of the repository"
