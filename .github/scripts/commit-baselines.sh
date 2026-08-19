#!/usr/bin/env bash
# Commits regenerated baseline screenshots to the pull request's branch, if any
# of them actually changed.
#
# `set -euo pipefail` matters more here than in most scripts: without `-e` a
# failed `git push` still leaves the step green, and the workflow would then
# report that baselines were accepted when nothing reached the branch.
#
# Usage: commit-baselines.sh <path>...
# Takes one or more paths to `git add`, rather than a hardcoded snapshots
# directory — this script has no opinion on which app's baselines it commits,
# so a second Playwright suite (e.g. frontend-admin's, once it has one) is a
# second argument at the call site, not a change here.
#
# Environment: HEAD_REF — the branch to push to, resolved by the guard step.
set -euo pipefail

if [ "$#" -eq 0 ]; then
    echo "Usage: $0 <path>..." >&2
    echo "At least one path is required — there is no default to silently commit." >&2
    exit 1
fi

git config user.name "github-actions[bot]"
git config user.email "github-actions[bot]@users.noreply.github.com"

git add -- "$@"

# `--cached --quiet` asks whether the staged set differs, which is the only
# question worth asking: an empty commit would re-trigger the visual check for
# no reason and add a commit that changed nothing.
if git diff --cached --quiet; then
    echo "changed=false" >> "$GITHUB_OUTPUT"
    exit 0
fi

git commit -m "chore: accept visual baselines (via /update-snapshots)"
git push origin "HEAD:${HEAD_REF}"
echo "changed=true" >> "$GITHUB_OUTPUT"
