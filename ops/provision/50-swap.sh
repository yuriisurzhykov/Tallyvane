#!/usr/bin/env bash
# A swap file, and a bias against actually using it.
#
# Numbered before Docker on purpose: containers benefit from swap existing when they start,
# and nothing here depends on anything Docker installs.
#
# What swap buys on this machine is a spike surviving as slowness instead of the kernel
# killing a container outright. What it costs is that a page which got written out is read
# back from disk — for PostgreSQL a slow query, for a JVM a garbage collection pause measured
# in seconds. So it is wanted as insurance and not as routine, which is what the swappiness
# value below is for.
#
# Measured, and worth knowing before reading docker-compose.yml: a container with `mem_limit`
# and nothing else gets `memory.swap.max` equal to that limit, so it may use the same amount
# again in swap. `memswap_limit` set equal to `mem_limit` makes it zero. Nothing here sets
# that: with swappiness this low the kernel will not write those pages out unless it is
# genuinely pressed, and forbidding swap outright brings back "spike kills the container".

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib.sh"

require_root
step "Swap"

SWAP_FILE="${TALLYVANE_SWAP_FILE:-/swapfile}"
SWAP_SIZE="${TALLYVANE_SWAP_SIZE:-2G}"
SWAPPINESS="${TALLYVANE_SWAPPINESS:-20}"

# A swap file on a copy-on-write filesystem needs handling this script does not do, and the
# failure is confusing rather than loud. Checking is one line.
root_fs="$(findmnt -no FSTYPE /)"
case "$root_fs" in
ext4 | ext3 | xfs) ok "root filesystem is $root_fs" ;;
*) die "root filesystem is $root_fs; a swap file there needs steps this script does not take" ;;
esac

wanted_bytes="$(numfmt --from=iec "$SWAP_SIZE")"

if [ -f "$SWAP_FILE" ]; then
  actual_bytes="$(stat -c %s "$SWAP_FILE")"
  if [ "$actual_bytes" -ne "$wanted_bytes" ]; then
    # Deliberately not resizing. Doing so means `swapoff` first, which pulls every swapped
    # page back into memory and can fail outright on a machine that is already short of it.
    # That is an operator's decision, not a script's.
    die "$SWAP_FILE is $(numfmt --to=iec "$actual_bytes"), wanted $SWAP_SIZE. Resizing needs swapoff, which can fail under memory pressure — do it by hand when the machine is idle"
  fi
  ok "$SWAP_FILE already $SWAP_SIZE"
else
  info "creating $SWAP_FILE of $SWAP_SIZE"
  # fallocate is instant and correct on ext4. If it ever produces a sparse file, mkswap or
  # swapon refuses rather than half-working, so the failure is visible.
  fallocate -l "$SWAP_SIZE" "$SWAP_FILE" || dd if=/dev/zero of="$SWAP_FILE" bs=1M count=$((wanted_bytes / 1024 / 1024)) status=none
  chmod 600 "$SWAP_FILE"
  mkswap "$SWAP_FILE" >/dev/null
fi

chmod 600 "$SWAP_FILE"

if swapon --show=NAME --noheadings | grep -qx "$SWAP_FILE"; then
  ok "already in use"
else
  swapon "$SWAP_FILE"
  info "enabled"
fi

# Without this the swap file is gone after a reboot, and the machine that survived a spike in
# testing dies on the next one.
if grep -qE "^[^#]*[[:space:]]swap[[:space:]]" /etc/fstab && grep -qF "$SWAP_FILE" /etc/fstab; then
  ok "already in /etc/fstab"
else
  printf '%s none swap sw 0 0\n' "$SWAP_FILE" >>/etc/fstab
  info "added to /etc/fstab"
fi

write_file /etc/sysctl.d/60-tallyvane-swap.conf 644 <<CONFIG
# Managed by the 'swap' step in ops/provision/.
#
# Ubuntu's default is 60, which reclaims process memory about as readily as file cache. 20
# keeps swap as insurance: the kernel prefers dropping cache, and writes pages out only under
# real pressure. Note that 0 would not disable swap — it means "only to avoid killing
# something".
vm.swappiness = $SWAPPINESS
CONFIG

if [ "$FILE_CHANGED" = yes ]; then
  sysctl --system >/dev/null
  info "applied sysctl settings"
fi

# Reading back what the kernel reports, not the file just written.
effective="$(cat /proc/sys/vm/swappiness)"
[ "$effective" = "$SWAPPINESS" ] || die "kernel reports vm.swappiness=$effective, wanted $SWAPPINESS"
ok "vm.swappiness = $effective"

swapon --show | sed 's/^/     /'
free -h | sed 's/^/     /'
