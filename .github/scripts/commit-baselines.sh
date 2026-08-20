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
# `/update-snapshots frontend-web` and `/update-snapshots packages/storybook`
# commented close together start two independent workflow runs that both
# resolve the same `head_sha` and both end up here — whichever pushes first
# wins, and the other's plain `git push` would be rejected as a non-fast-forward.
# The retry loop below is safe specifically because it is a rebase, not a
# force-push: each module writes only under its own `tests/visual-snapshots/`,
# so replaying this commit onto whatever the branch became is conflict-free.
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

attempt=1
max_attempts=5
while ! git push origin "HEAD:${HEAD_REF}"; do
    if [ "$attempt" -ge "$max_attempts" ]; then
        echo "::error::Still rejected after $max_attempts rebase attempts against ${HEAD_REF} — a real conflict, not just a race." >&2
        exit 1
    fi
    git fetch origin "${HEAD_REF}"
    git rebase FETCH_HEAD
    attempt=$((attempt + 1))
done
echo "changed=true" >> "$GITHUB_OUTPUT"
