# .cursor/hooks

Real enforcement for `.cursor/rules/frontend-command-harness.mdc`, together
with the rest of the harness: `.cursor/lib/actions.mjs` and
`.cursor/lib/compact-output.mjs` (the shared logic) and
`.cursor/cli/agent-check.mjs` (the one script agents run). Documented here as
one slice rather than split three ways, because that's what it is — a hook
alone, or a wrapper alone, doesn't answer the actual question this exists
for.

## What needed doing

A rules file is read, not obeyed — it can drift out of context, or simply be
skipped under pressure. This repo has already shipped a real bug from
exactly the class of mistake a "pick the right pnpm form" rule invites (see
`.github/scripts/README.md`'s postmortem on name-vs-path package
addressing), so "written down somewhere" was not enough here specifically.

## What was actually done, including a corrected wrong turn

**First draft:** a hook that allowed the *correct* pnpm invocation for each
action (`pnpm run typecheck`, `pnpm --filter "./frontend-web" run test:e2e`,
...) and denied four specific wrong forms — `npm`/`yarn`, a name-based or
unrooted `--filter`, `cd`-then-`pnpm run`, and a bare direct-binary bypass.
It worked, but it left the actual problem in place: an agent still chose
*which* pnpm command to run for a given action, and still saw that command's
full raw output land in context — `tsc`, `eslint`, `vitest`, `playwright` and
`next build` each have their own verbose format, none of it written for a
context window.

**What that surfaced:** asked directly, the answer was that agents shouldn't
be deciding "when and what to run" *at all* — there should be one script,
and it should hand back only what's necessary to know the result, not the
full log.

**The correction:** `.cursor/cli/agent-check.mjs` is that one script —
`node .cursor/cli/agent-check.mjs <action> [--package <path>]`. It builds
the correct pnpm invocation itself (via `.cursor/lib/actions.mjs`, the same
module the hook imports, so the two can't drift apart), always writes the
underlying tool's full output to a timestamped log under
`.cursor/agent-check-logs/`, and prints only `OK <action>` on success or a
compact `file:line — message` digest (via `.cursor/lib/compact-output.mjs`)
plus the log path on failure. The hook's rule collapsed to one, simpler
policy: any direct pnpm/npm/yarn/npx invocation of a controlled action —
correctly formed or not — is denied in favor of the wrapper; the four
separate "wrong form" checks from the first draft are gone because there is
no longer a "right form" for an agent to reach for directly at all.

**A second wrong turn, in the directory name itself**: the wrapper first
lived at `.cursor/scripts/agent-check.mjs`, borrowed from `frontend-web/scripts/`
and `frontend-admin/scripts/`. Those hold a single app's own dev utilities
(`generate-design-tokens.ts`, `contrast-table.ts`) — a different level of
responsibility entirely from a repo-wide, agent-invoked entry point that no
`package.json` even knows about. This repo already had the right name for
that distinction: `packages/test-kit/src/cli/` (`run-scoped.mjs`,
`free-ports.mjs`) already separates "directly-executable entry point" from
"logic to import," which is exactly the split `.cursor/lib/` vs. the wrapper
needed. Renamed to `.cursor/cli/agent-check.mjs` to follow that existing
convention instead of the wrong one.

**One more real bug found while correcting course**: the first version of
the "is this a controlled action" check matched a script name as a regex
word inside the command string. `pnpm why lint-staged` denied itself, because
`\blint\b` matches "lint" inside "lint-staged" — a word boundary exists at
the hyphen. Fixed by tokenizing the command (respecting quoted strings) and
comparing whole tokens after the package-manager name, never a
substring/regex match — `enforce-frontend-commands.test.mjs` keeps
`pnpm why lint-staged`, `pnpm add -D eslint-plugin-foo` and similar in its
allow-list specifically so this can't come back silently.

## Consuming it

```bash
node .cursor/cli/agent-check.mjs verify
node .cursor/cli/agent-check.mjs typecheck --package packages/frontend-shared
node .cursor/cli/agent-check.mjs test:e2e --package frontend-web
```

Self-tests, runnable directly with plain `node` (no workspace dependency —
these run outside the pnpm workspace entirely):

```bash
node .cursor/lib/compact-output.test.mjs
node .cursor/cli/agent-check.test.mjs
node .cursor/hooks/enforce-frontend-commands.test.mjs
```

`hooks.json` registers the hook on `beforeShellExecution` with a broad
pre-filter matcher (`pnpm|npm|yarn|npx|node_modules`) so it's skipped
entirely for commands that could never be a workspace-script invocation, and
`failClosed: false` — a bug in this hook must not brick the agent's ability
to use a shell at all; the self-tests above are what earns the hook's trust,
not the registration's fail-open/fail-closed setting.

## Why this is understandable, scalable, extensible

Three files, three responsibilities, none of them duplicating another's
knowledge: `.cursor/lib/actions.mjs` knows *which* actions exist and how each
is invoked; `.cursor/lib/compact-output.mjs` knows how to compress a tool's
raw output; `.cursor/cli/agent-check.mjs` wires those two into a runnable
command; `.cursor/hooks/enforce-frontend-commands.mjs` only has to know "is
this the wrapper, or a direct invocation of one of `actions.mjs`'s names" —
it imports the list rather than keeping its own copy. Adding a tenth
workspace member changes nothing in any of these four files; adding a
genuinely new *category* of controlled action is one entry in
`CONTROLLED_ACTIONS`, read by both the wrapper and the hook from the same
place.

## Migration and fault tolerance

Nothing here holds state or touches the repository — a deny is a same-turn
JSON response, never a partial side effect to unwind, and the wrapper's own
side effect (a log file under `.cursor/agent-check-logs/`, already covered by
the repo's `*.log` gitignore rule) is disposable by design. If the hook
itself throws or produces invalid JSON, `failClosed: false` means Cursor
fails open (allows the command) rather than locking the agent out of the
shell entirely — the deliberately safer failure mode for a hook that governs
day-to-day tool use rather than something security-critical. If the
wrapper's compact-output heuristic misses a failure shape it's never seen,
the full raw log is still on disk, named in the same line that printed the
digest — a miss costs one extra read, never the actual failure information.

## SOLID

**Single responsibility**, applied literally across the split above — each
file answers exactly one question, and the corrected wrong turn above is a
direct example of what happens when the split is too coarse (the first
draft's hook conflated "what's a controlled action" with "how do I detect
someone bypassing it," which is why it needed four separate rules where one,
plus a shared list, now suffices).

**Open/closed.** A new controlled action is an entry in
`CONTROLLED_ACTIONS`; a new tool's failure format is a new branch in
`compact-output.mjs`'s extractor. Neither requires touching the hook.

**Dependency inversion.** The hook and the wrapper both depend on
`.cursor/lib/actions.mjs`'s exported shape, never on a re-derived or
hand-copied list of script names — the one place that knows what's
controlled is the one place that would need to change if that ever did.
