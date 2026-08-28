#!/usr/bin/env bash
# Docker from Docker's own repository, and a bound on how much disk logs may take.
#
# Ubuntu's packaged docker.io lags behind and does not carry the compose plugin, which this
# deployment needs — `docker compose`, not the retired `docker-compose` script.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib.sh"

require_root
step "Docker"

# shellcheck disable=SC1091
. /etc/os-release
codename="${UBUNTU_CODENAME:-${VERSION_CODENAME:-}}"
[ -n "$codename" ] || die "cannot tell which release this is; no codename in /etc/os-release"

if [ ! -s /etc/apt/keyrings/docker.asc ]; then
  info "fetching Docker's signing key"
  install -d -m 755 /etc/apt/keyrings
  curl --fail --silent --show-error --location https://download.docker.com/linux/ubuntu/gpg \
    -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
else
  ok "signing key present"
fi

write_file /etc/apt/sources.list.d/docker.list 644 <<CONFIG
# Managed by the 'docker' step in ops/provision/.
deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $codename stable
CONFIG

if [ "$FILE_CHANGED" = yes ]; then
  DEBIAN_FRONTEND=noninteractive apt-get update --quiet
fi

apt_install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# The container log driver is unbounded by default: json-file grows until the disk is gone.
# Repeated in ops/docker-compose.yml per service, because this file is not in the repository
# and a rebuilt machine would otherwise come up with no bound and say nothing about it.
write_file /etc/docker/daemon.json 644 <<'CONFIG'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "50m",
    "max-file": "5"
  }
}
CONFIG

dockerd --validate --config-file /etc/docker/daemon.json >/dev/null ||
  die "dockerd rejected daemon.json; not restarting"

if [ "$FILE_CHANGED" = yes ]; then
  systemctl restart docker
  info "restarted the daemon so the new configuration takes effect"
fi
systemctl enable --now docker >/dev/null 2>&1 || die "docker failed to start"

# A valid daemon.json is not a daemon running it. This exact gap cost an hour once: the file
# passed --validate, the log bound was not in effect, and nothing anywhere said why.
applied_since /etc/docker/daemon.json docker ||
  die "daemon.json is newer than the running daemon — restart it: systemctl restart docker"
ok "daemon started after the current daemon.json, so its settings are in effect"

if id -nG "$TALLYVANE_USER" | tr ' ' '\n' | grep -qx docker; then
  ok "$TALLYVANE_USER already in the docker group"
else
  # Worth knowing what this grants: the docker socket is root on the host by another route,
  # since anyone who can start a container can mount / into it. The alternative is rootless
  # Docker, which this deployment has not evaluated.
  info "adding $TALLYVANE_USER to the docker group"
  usermod --append --groups docker "$TALLYVANE_USER"
  warn "$TALLYVANE_USER must log out and back in before the group takes effect"
fi

ok "$(docker --version), $(docker compose version)"
