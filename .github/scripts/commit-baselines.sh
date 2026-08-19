#!/usr/bin/env bash
# Commits regenerated baseline screenshots to the pull request's branch, if any
# of them actually changed.
#
# `set -euo pipefail` matters more here than in most scripts: without `-e` a
# failed `git push` still leaves the step green, and the workflow would then
# report that baselines were accepted when nothing reached the branch.
#
# Environment: HEAD_REF — the branch to push to, resolved by the guard step.
set -euo pipefail

git config user.name "github-actions[bot]"
git config user.email "github-actions[bot]@users.noreply.github.com"

git add frontend/tests/visual-snapshots

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
