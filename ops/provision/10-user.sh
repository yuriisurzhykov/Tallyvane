#!/usr/bin/env bash
# The unprivileged account everything else runs as, and its SSH key.
#
# This has to finish before the 'ssh' step disables password authentication. Get the order
# wrong and the machine locks everyone out — which is why that step refuses to run until a
# valid key is present here, rather than trusting that this one was executed first.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib.sh"

require_root
step "Account $TALLYVANE_USER"

if id -u "$TALLYVANE_USER" >/dev/null 2>&1; then
  ok "user exists"
else
  info "creating user"
  useradd --create-home --shell /bin/bash "$TALLYVANE_USER"
fi

if id -nG "$TALLYVANE_USER" | tr ' ' '\n' | grep -qx sudo; then
  ok "already in the sudo group"
else
  info "adding to the sudo group"
  usermod --append --groups sudo "$TALLYVANE_USER"
fi

ssh_dir="/home/$TALLYVANE_USER/.ssh"
keys_file="$ssh_dir/authorized_keys"
install -d -m 700 -o "$TALLYVANE_USER" -g "$TALLYVANE_USER" "$ssh_dir"
[ -f "$keys_file" ] || install -m 600 -o "$TALLYVANE_USER" -g "$TALLYVANE_USER" /dev/null "$keys_file"

# Pasting the key here rather than telling the operator to run ssh-copy-id from another
# terminal: at this point password authentication is still enabled, so ssh-copy-id would
# work — but it needs a second session against a machine that may not be reachable yet, and
# the failure mode of "I thought I did it" is a locked-out server.
if authorized_key_present; then
  ok "authorized_keys already holds a valid public key"
else
  while ! authorized_key_present; do
    printf '\n'
    warn "no public key for $TALLYVANE_USER yet"
    cat <<'GUIDE'
  Paste the PUBLIC half of your SSH key — one line, starts with ssh-ed25519 or ssh-rsa.
  On your own machine it is printed by:

      cat ~/.ssh/id_ed25519.pub

  If you have no key yet, make one there first (never on this server):

      ssh-keygen -t ed25519 -C "you@yourmachine"

GUIDE
    read -r -p '  key: ' pasted </dev/tty || die "no terminal to ask on"
    [ -n "$pasted" ] || continue

    candidate="$(mktemp)"
    printf '%s\n' "$pasted" >"$candidate"
    if ! ssh-keygen -l -f "$candidate" >/dev/null 2>&1; then
      rm -f "$candidate"
      warn "that is not a valid public key — check you copied the .pub file, not the private one"
      continue
    fi
    if grep -qxF "$pasted" "$keys_file"; then
      ok "key already present"
    else
      cat "$candidate" >>"$keys_file"
      info "key added: $(ssh-keygen -l -f "$candidate")"
    fi
    rm -f "$candidate"
  done
  ok "authorized_keys verified"
fi

chown "$TALLYVANE_USER:$TALLYVANE_USER" "$keys_file"
chmod 600 "$keys_file"
ok "account ready"
