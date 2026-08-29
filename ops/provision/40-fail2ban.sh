#!/usr/bin/env bash
# Ban an address that keeps failing to authenticate.
#
# The firewall's `limit` rule slows a brute-force attempt down; this stops one. They are not
# redundant: ufw counts connections without knowing whether they succeeded, fail2ban reads
# the outcome.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib.sh"

require_root
step "fail2ban"

apt_install fail2ban

# `backend = systemd` is the whole reason this file exists. Ubuntu logs authentication to the
# journal and ships no /var/log/auth.log, so the stock sshd jail watches a file that never
# appears — it starts, reports itself enabled, and bans nobody.
#
# No thresholds here: fail2ban's own defaults apply, and picking numbers is a decision with
# nothing measured behind it yet.
write_file /etc/fail2ban/jail.d/tallyvane.conf 644 <<'CONFIG'
# Managed by the 'fail2ban' step in ops/provision/.
[sshd]
enabled = true
backend = systemd
CONFIG

systemctl enable --now fail2ban >/dev/null 2>&1 || die "fail2ban failed to start"
if [ "$FILE_CHANGED" = yes ]; then
  systemctl restart fail2ban
  info "restarted fail2ban"
fi

# Asking fail2ban what it is actually watching, rather than reading back the file just
# written. A jail that failed to load still leaves the file looking correct.
fail2ban-client status sshd >/dev/null 2>&1 || die "the sshd jail is not running"
ok "sshd jail active"
fail2ban-client status sshd | sed 's/^/     /'
