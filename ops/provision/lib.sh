# Sourced by every provisioning step, never executed on its own.
#
# Two things live here rather than in each step. The reporting vocabulary, so a run reads as
# one log instead of nine dialects. And `manual_step` — the pause for work a script cannot
# do — which checks the result rather than trusting the answer: a step that accepts "yes" as
# proof is a step that will one day be skipped by a tired operator and noticed a month later.

if [ -n "${TALLYVANE_LIB_SOURCED:-}" ]; then
  return 0
fi
TALLYVANE_LIB_SOURCED=1

set -euo pipefail

TALLYVANE_USER="${TALLYVANE_USER:-deploy}"
TALLYVANE_APP_DIR="${TALLYVANE_APP_DIR:-/srv/apps/tallyvane}"
TALLYVANE_SECRETS_DIR="${TALLYVANE_SECRETS_DIR:-/srv/secrets/tallyvane}"

# The user the cloudflared image runs as. Read from the image rather than remembered:
#   docker image inspect cloudflare/cloudflared:<tag> --format '{{.Config.User}}'
# The credentials file is owned by this id and readable by nothing else, which is what lets
# the container keep its own non-root user instead of being given root to read one file.
TALLYVANE_TUNNEL_UID="${TALLYVANE_TUNNEL_UID:-65532}"

info() { printf '     %s\n' "$*"; }
ok() { printf '  ok %s\n' "$*"; }
warn() { printf '  !! %s\n' "$*" >&2; }
step() { printf '\n== %s\n' "$*"; }

die() {
  printf '\nFAILED: %s\n' "$*" >&2
  exit 1
}

require_root() {
  [ "$(id -u)" -eq 0 ] || die "run as root or under sudo: sudo $0"
}

# Installs only what is missing, so a re-run costs a dpkg query rather than a download.
apt_install() {
  local missing=()
  local package
  for package in "$@"; do
    dpkg-query --show --showformat='${db:Status-Status}\n' "$package" 2>/dev/null |
      grep -q '^installed$' || missing+=("$package")
  done
  if [ ${#missing[@]} -eq 0 ]; then
    ok "already installed: $*"
    return 0
  fi
  info "installing: ${missing[*]}"
  DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends "${missing[@]}"
}

# Writes stdin to a file and reports whether anything changed, so a caller can restart a
# service only when it has to. FILE_CHANGED is the answer; there is no return code for it
# because a non-zero return under `set -e` would end the run instead of answering a question.
FILE_CHANGED=no
write_file() {
  local path="$1" mode="$2" owner="${3:-root:root}"
  local tmp
  tmp="$(mktemp)"
  cat >"$tmp"
  if [ -f "$path" ] && cmp -s "$tmp" "$path"; then
    rm -f "$tmp"
    FILE_CHANGED=no
    ok "$path already correct"
    return 0
  fi
  install -D -m "$mode" -o "${owner%%:*}" -g "${owner##*:}" "$tmp" "$path"
  rm -f "$tmp"
  # shellcheck disable=SC2034  # read by the step scripts that source this file.
  FILE_CHANGED=yes
  info "wrote $path"
}

# A step a script cannot perform: a browser login, a key only the operator has, a setting in
# somebody else's control panel.
#
# `verify` is a shell command that must succeed once the step is genuinely done. It runs
# first, which is what makes the whole run idempotent — an already-provisioned machine walks
# through without asking anything. Input is read from the terminal directly so this still
# works when the run is piped through `tee`.
#
# `verify` is evaluated in this shell rather than a subshell, so a step can hand over the name
# of a function it defines instead of squeezing the check into one quoted line.
manual_step() {
  local title="$1" verify="$2" instructions="$3"
  local output
  if output="$(eval "$verify" 2>&1)"; then
    ok "$title — already done"
    return 0
  fi
  local answer
  while true; do
    printf '\n'
    warn "manual step: $title"
    printf '%s\n\n' "$instructions"
    read -r -p '  [Enter] when done, [s]kip, [q]uit: ' answer </dev/tty || die "no terminal to ask on"
    case "$answer" in
    q | Q) die "aborted at: $title" ;;
    s | S)
      warn "skipped WITHOUT verification: $title"
      return 0
      ;;
    esac
    if output="$(eval "$verify" 2>&1)"; then
      ok "$title — verified"
      return 0
    fi
    # Showing what the check said. A first version discarded this, and the result was a loop
    # that repeated the same instructions while knowing exactly why they were not working.
    warn "still not verified. The check that has to pass: $verify"
    [ -n "$output" ] && printf '%s\n' "$output" | sed 's/^/     /'
  done
}

# A pause for something this machine genuinely cannot check — a setting in the hosting
# provider's control panel, a fact only observable from outside. Separate from `manual_step`
# on purpose: inventing a check that always passes would make an unverified step look
# verified, which is worse than admitting there is no check.
manual_notice() {
  local title="$1" instructions="$2"
  printf '\n'
  warn "manual step, NOT verified by this script: $title"
  printf '%s\n\n' "$instructions"
  read -r -p '  [Enter] to continue, [q]uit: ' answer </dev/tty || die "no terminal to ask on"
  case "$answer" in
  q | Q) die "aborted at: $title" ;;
  esac
}

# True when the file has not been changed since the service last started — the difference
# between a correct configuration file and a configuration in effect. Docker's log bounds sat
# in a valid daemon.json for an hour without applying, for exactly this reason.
applied_since() {
  local path="$1" unit="$2" started
  [ -f "$path" ] || return 1
  started="$(systemctl show "$unit" --property=ExecMainStartTimestamp --value)"
  [ -n "$started" ] || return 1
  [ "$(date -d "$started" +%s)" -ge "$(stat -c %Y "$path")" ]
}

authorized_key_present() {
  local file="/home/$TALLYVANE_USER/.ssh/authorized_keys"
  [ -s "$file" ] && ssh-keygen -l -f "$file" >/dev/null 2>&1
}
