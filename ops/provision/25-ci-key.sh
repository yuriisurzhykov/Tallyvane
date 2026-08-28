#!/usr/bin/env bash
# A second, far more restricted key in the same authorized_keys — CI's own, forced to run
# exactly one command (deploy-wrapper.sh) no matter what it asks to run.
#
# `restrict` (OpenSSH 7.2+) is no-pty,no-agent-forwarding,no-X11-forwarding,no-port-forwarding,
# no-user-rc in one word: a session opened with this key can run the named command and nothing
# else, regardless of which groups $TALLYVANE_USER belongs to (sudo, docker, …). That is the
# actual boundary this key relies on — see ops/README.md's dated entry on why a second,
# less-privileged system user would not have added one: `docker` group membership is already
# root-equivalent on this host, forced-command is what a leaked key cannot escape either way.
#
# Optional, unlike the personal key the 'user' step requires: a server with no CD wired up yet
# has no CI keypair to add. This step says so and moves on rather than blocking the rest of
# provisioning on a decision that belongs to a later stage — re-run it alone once GitHub Actions
# has one: `sudo ops/provision/provision.sh --only ci-key`.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib.sh"

require_root
step "CI deploy key"

keys_file="/home/$TALLYVANE_USER/.ssh/authorized_keys"
command_path="$TALLYVANE_APP_DIR/deploy-wrapper.sh"
option_prefix="restrict,command=\"$command_path\""

ci_key_present() {
  grep -qF "command=\"$command_path\"" "$keys_file" 2>/dev/null
}

if ci_key_present; then
  ok "a CI deploy key is already present"
else
  warn "no CI deploy key yet"
  cat <<GUIDE
  This step is optional — press Enter with nothing typed to skip it if GitHub Actions has no
  deploy key yet.

  To add one: generate a keypair FOR THIS PURPOSE ONLY, on your own machine, never on this
  server:

      ssh-keygen -t ed25519 -f tallyvane-ci-deploy -N "" -C "github-actions@tallyvane"

  Put the PRIVATE half (tallyvane-ci-deploy) into the repository's GitHub secret named
  TALLYVANE_DEPLOY_KEY, and paste the PUBLIC half (tallyvane-ci-deploy.pub) below — one line,
  starts with ssh-ed25519.

GUIDE
  while ! ci_key_present; do
    read -r -p '  key (or [Enter] to skip): ' pasted </dev/tty || die "no terminal to ask on"
    if [ -z "$pasted" ]; then
      warn "skipped — re-run 'provision.sh --only ci-key' once a keypair exists"
      break
    fi

    candidate="$(mktemp)"
    printf '%s\n' "$pasted" >"$candidate"
    if ! ssh-keygen -l -f "$candidate" >/dev/null 2>&1; then
      rm -f "$candidate"
      warn "that is not a valid public key — check you copied the .pub file, not the private one"
      continue
    fi

    printf '%s %s\n' "$option_prefix" "$pasted" >>"$keys_file"
    info "added: $(ssh-keygen -l -f "$candidate")"
    rm -f "$candidate"
  done
fi

chown "$TALLYVANE_USER:$TALLYVANE_USER" "$keys_file"
chmod 600 "$keys_file"
ok "CI deploy key step done"
