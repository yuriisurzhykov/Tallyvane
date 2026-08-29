#!/usr/bin/env bash
# Two directories, and the reason they are two.
#
# The application directory is a copy of ops/ from the repository, and ops/deploy.sh syncs it
# with deletion — anything not in the repository is removed, which is how a route deleted from
# the repository stops being served. The secrets directory is deliberately outside that tree,
# so deletion can never reach the tunnel credentials.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib.sh"

require_root
step "Directories"

install -d -m 755 -o "$TALLYVANE_USER" -g "$TALLYVANE_USER" "$TALLYVANE_APP_DIR"
# 700: the tunnel credentials live here. Nothing but the owner has any business listing it.
install -d -m 700 -o "$TALLYVANE_USER" -g "$TALLYVANE_USER" "$TALLYVANE_SECRETS_DIR"

for directory in "$TALLYVANE_APP_DIR" "$TALLYVANE_SECRETS_DIR"; do
  ok "$(stat -c '%A %U:%G %n' "$directory")"
done

[ "$(stat -c %a "$TALLYVANE_SECRETS_DIR")" = 700 ] ||
  die "$TALLYVANE_SECRETS_DIR is not 700"
