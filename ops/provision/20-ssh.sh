#!/usr/bin/env bash
# Key-only SSH, no root login.
#
# Written as a drop-in rather than by editing /etc/ssh/sshd_config, so a distribution upgrade
# replacing that file cannot silently take these settings with it.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib.sh"

require_root
step "SSH"

# The guard, not a formality: disabling password authentication with no usable key is how a
# machine becomes reachable only through the provider's web console.
authorized_key_present ||
  die "$TALLYVANE_USER has no valid public key — run the 'user' step first, or this one locks everyone out"
ok "$TALLYVANE_USER has a key: $(ssh-keygen -l -f "/home/$TALLYVANE_USER/.ssh/authorized_keys" | head -n 1)"

# A drop-in that nothing includes is a file that reads as configuration and behaves as a
# comment.
grep -qE '^\s*Include\s+/etc/ssh/sshd_config\.d/\*\.conf' /etc/ssh/sshd_config ||
  die "/etc/ssh/sshd_config does not include sshd_config.d/*.conf — the drop-in would be ignored"

write_file /etc/ssh/sshd_config.d/10-tallyvane.conf 644 <<'CONFIG'
# Managed by the 'ssh' step in ops/provision/.
PermitRootLogin no
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
CONFIG

sshd -t || die "sshd rejected the configuration; nothing was reloaded"
ok "sshd accepts the configuration"

if [ "$FILE_CHANGED" = yes ]; then
  # Ubuntu activates sshd through a socket, so each new connection starts a process that
  # reads the current configuration and nothing needs reloading. A long-running ssh.service
  # does need it. Both arrangements exist in the wild, so handle both rather than assume.
  if systemctl is-active --quiet ssh; then
    systemctl reload ssh
    info "reloaded ssh.service"
  else
    info "sshd is socket-activated; the next connection reads the new configuration"
  fi
fi

for setting in \
  'permitrootlogin no' \
  'pubkeyauthentication yes' \
  'passwordauthentication no' \
  'kbdinteractiveauthentication no'; do
  sshd -T | grep -qx "$setting" || die "sshd reports something other than: $setting"
done
ok "verified against sshd's own effective configuration, not against the file"

manual_notice "keep this session open" "$(
  cat <<'GUIDE'
  Open a SECOND terminal and connect with the key before closing this one:

      ssh deploy@<this-server>

  If that fails, fix it from here — this session is the way back in.
GUIDE
)"
