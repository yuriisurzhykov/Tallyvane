# .github/scripts

Real `.mjs`/`.sh` files backing the workflows in `.github/workflows/`, per this
repo's own rule: CI logic is not inline YAML. `lib/actions.mjs` is the reason
that rule is enforceable rather than aspirational — a hand-written, dozen-line
GitHub API client (see its own header comment for why it exists instead of
`actions/github-script`) that every script here imports instead of reaching
for a third-party action inside the one job that holds a write-scoped token.

## The `/update-snapshots` pipeline

`accept-visual-baselines.yml` lets a maintainer accept new Playwright visual
baselines from a PR comment instead of a local Linux container (font
rendering differs by OS — see `docs/frontend/02-component-testing-architecture.md`
§3). Two jobs, three scripts:

- `baseline-request-guard.mjs` — parses `/update-snapshots <module>` off the
  comment, checks the commenter's association and that the PR's branch is not
  a fork, and resolves the module and branch into an immutable `head_sha` the
  second job checks out. Runs against this repository's own default-branch
  copy (no `ref:` on its checkout), specifically so the PR's version of the
  code cannot vouch for itself.
- `commit-baselines.sh` — regenerates baselines, stages, commits, and pushes
  them back to the PR's branch, which re-triggers `visual-tests.yml`.
- `baseline-result-comment.mjs` — reports the outcome (changed / nothing to
  accept / failed) back to the PR, always, including the "nothing changed"
  case — a command that silently does nothing is indistinguishable from one
  that silently broke.

## Two real bugs found running this live, not hypothetical

### `<module>` was checked as a shape, never as a real path

`baseline-request-guard.mjs`'s `MODULE_PATTERN` regex only ever rejected shell
metacharacters. It happily accepted `storybook` — the workspace member's
`package.json` `name`, not its path — because that string is exactly as
shaped as the real path `packages/storybook`. Nothing caught the difference:
the guard's own checkout is deliberately sparse and pinned to the default
branch, so it cannot `fs.existsSync` its way to a real answer, and
`setup-playwright`'s `pnpm --filter "./$MODULE"` silently exits 0 when no
project matches instead of failing. The first visible failure was three steps
downstream, in the `update-snapshots` job's "Regenerate baselines" step,
as a bare `Error: An error occurred trying to start process '/usr/bin/bash'
with working directory '.../storybook'. No such file or directory` — a
runner-level error message that names none of the three actual causes.

Fixed by checking existence where it can actually be checked: over the
Contents API, against the pull request's own tree at `head.sha` (the same SHA
the second job later checks out), added as `apiGetOrNull` alongside the
existing `api()` in `lib/actions.mjs` rather than teaching `api()` itself to
swallow one specific status code for one specific caller. A 404, or a path
that resolves to a file instead of a directory, now gets the same
`commentUsage()` PR comment already written for a malformed module string,
naming the actual mistake (`storybook` is a package name, not a path) instead
of a bash error three jobs removed from it.

### Two `/update-snapshots` comments race each other's push

`/update-snapshots frontend-web` and `/update-snapshots packages/storybook`
posted close together on the same PR start two independent workflow runs.
Both resolve the same `head_sha`, both build their own commit as its child,
and `commit-baselines.sh`'s `git push origin HEAD:$HEAD_REF` had no retry —
whichever job pushed first won, and the other's push was rejected outright as
a non-fast-forward, losing that module's baseline update entirely and
requiring the comment to be posted again.

Fixed with a bounded retry loop around the push: on rejection, `git fetch
origin "$HEAD_REF"` then `git rebase FETCH_HEAD`, then retry, up to five
attempts before failing loudly. This is deliberately a rebase, not a
force-push, and it is safe as a *retry* (not just a race made narrower)
specifically because each module writes only under its own
`tests/visual-snapshots/` subtree — two concurrent baseline commits never
touch the same file, so replaying one on top of the other is always
conflict-free, not merely usually. The retry's rebase needs a real
merge-base with the branch's tip to work at all, which the job's default
shallow (`fetch-depth: 1`) checkout does not carry post-race — `fetch-depth:
0` on that one checkout step in `accept-visual-baselines.yml` is load-bearing
for exactly this reason, not an unrelated cleanup.

## Why this is understandable, scalable, extensible

Every script does one job named by its filename, imports the same small
`lib/actions.mjs`, and reads its inputs only from documented environment
variables (each script's own header lists them) — nothing here reaches into
`github.event` directly except at the workflow-file call site, so a script
can be read, tested, or reused without also reading the YAML that invokes it.
Adding a fourth workspace member with a visual suite (`frontend-admin`) is a
new matrix entry in `visual-tests.yml` and nothing else here changes, because
`<module>` and `HEAD_REF` are already caller-supplied, never hardcoded.

## Fault tolerance

A failed API call, a missing environment variable, or a rejected push all
fail the step loudly (`run()`'s `::error::` annotation, or bash's `set -e`) —
none of them can leave a step green while having done nothing, which is the
one failure mode this pipeline cannot afford: it is the only place baselines
are ever written, and a check that can silently fail to write what it claims
to have written is worse than one that visibly fails.

## SOLID

**Single responsibility.** Each script answers exactly one question — may
this proceed (`baseline-request-guard.mjs`), commit what changed
(`commit-baselines.sh`), report what happened
(`baseline-result-comment.mjs`) — and none of them know how to do either of
the other two.

**Open/closed.** A fourth workspace member's visual suite, or a fifth
concurrent `/update-snapshots` comment, needs no change to any script here:
`module` and `HEAD_REF` are parameters, not branches in an `if`.

**Dependency inversion.** Every script depends on `lib/actions.mjs`'s small
interface (`api`, `apiGetOrNull`, `required`, `setOutput`, `run`), never on
`fetch` or `process.env` directly at more than one place — the one place that
knows how to talk to GitHub's API is the one place that would need to change
if it ever had to.
