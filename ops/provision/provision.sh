#!/usr/bin/env bash
# Runs every step in order. Safe to re-run: each step checks the machine before changing it,
# so a provisioned server walks through reporting what is already true.
#
#   sudo ops/provision/provision.sh                every step
#   sudo ops/provision/provision.sh --from docker   resume after an interruption
#   sudo ops/provision/provision.sh --only swap     one step
#   ops/provision/provision.sh --list               the steps, in order
#
# Steps are selected by name, not by the number in the file name, and the split is deliberate.
# The number orders the files so that `ls` shows the sequence at a glance; the name is what
# gets typed. Keeping them separate means the numbers can be made even again after an
# insertion without silently changing what `--from 60` written in somebody's notes refers to.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib.sh"

from=""
only=""
list_only=no

while [ $# -gt 0 ]; do
  case "$1" in
  --from)
    from="${2:?--from needs a step name}"
    shift 2
    ;;
  --only)
    only="${2:?--only needs a step name}"
    shift 2
    ;;
  --list)
    list_only=yes
    shift
    ;;
  *) die "unknown argument: $1" ;;
  esac
done

mapfile -t scripts < <(find "$SCRIPT_DIR" -maxdepth 1 -name '[0-9][0-9]-*.sh' -printf '%f\n' | sort)
[ ${#scripts[@]} -gt 0 ] || die "no step scripts next to $0"

step_name() {
  local without_number="${1#*-}"
  printf '%s' "${without_number%.sh}"
}

step_names() {
  local script
  for script in "${scripts[@]}"; do
    printf '%s\n' "$(step_name "$script")"
  done
}

index_of() {
  local wanted="$1" index=0 script
  for script in "${scripts[@]}"; do
    if [ "$(step_name "$script")" = "$wanted" ]; then
      printf '%s' "$index"
      return 0
    fi
    index=$((index + 1))
  done
  return 1
}

if [ "$list_only" = yes ]; then
  for script in "${scripts[@]}"; do
    printf '  %s  %s\n' "${script%%-*}" "$(step_name "$script")"
  done
  exit 0
fi

selected=()
if [ -n "$only" ]; then
  index="$(index_of "$only")" || die "no step named '$only'. Known: $(step_names | tr '\n' ' ')"
  selected=("${scripts[$index]}")
elif [ -n "$from" ]; then
  index="$(index_of "$from")" || die "no step named '$from'. Known: $(step_names | tr '\n' ' ')"
  selected=("${scripts[@]:$index}")
else
  selected=("${scripts[@]}")
fi

require_root

printf 'Provisioning %s\n' "$(hostname)"
printf 'Steps: %s\n' "$(
  for script in "${selected[@]}"; do printf '%s ' "$(step_name "$script")"; done
)"

for script in "${selected[@]}"; do
  bash "$SCRIPT_DIR/$script" || die "step '$(step_name "$script")' failed; fix it and resume with --from $(step_name "$script")"
done

step "Done"
info "next: from a clone of the repository, on your own machine, run"
info "  ops/deploy.sh $TALLYVANE_USER@$(hostname -I | awk '{print $1}')"
