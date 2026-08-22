# test-kit

Playwright accessibility, contrast and visual-regression checking logic —
WCAG/APCA tags, `SummaryReporter`, and the shared parts of a Playwright config
— held as its own workspace package, owned by none of the things it checks.

## What needed doing

`frontend-web/tests/e2e/` already had a working a11y/contrast/visual suite,
but it was reachable only from inside `frontend-web`: its own
`playwright.config.ts`, its own `package.json` scripts, its own
`working-directory: frontend-web` in CI. That was invisible while `frontend-web`
was the only Next.js app in the workspace. It stopped being invisible the
moment two more consumers needed the exact same checks — `packages/storybook`,
showcasing Tier 0 primitives in isolation, and `frontend-admin`, which
[ADR-032](../../docs/adr/ADR-032-subdomain-split-and-admin-isolation.md)
already made a genuinely separate application specifically so it could never
depend on `frontend-web`'s internals. Checking logic living inside
`frontend-web` but needed by `frontend-admin` would have been exactly the
asymmetric coupling ADR-032 forbids for application code, just relocated into
test code where nothing enforces it.

`.github/scripts/commit-baselines.sh` already anticipated this — it takes any
number of paths to commit rather than one hardcoded snapshot directory, with a
comment naming "a second Playwright suite (e.g. frontend-admin's, once it has
one)" as exactly this case. The baseline-acceptance half of the system was
already symmetric; the checking logic itself was not.

## What was actually done

The reusable parts of `frontend-web/tests/e2e/` moved here as functions
parameterized by a list, rather than files with the list baked in:

- `defineA11ySpecs(manifest, options?)` — structural accessibility (axe-core).
  Default surface is `"document"` (a real page). Isolated stories pass
  `{ surface: "component" }`, which skips a closed list of page-scoped rules
  owned here (`page-has-heading-one`, `landmark-one-main`, `bypass`,
  `document-title`, `region`) rather than a free-form `disableRules` array the
  consumer could drift. Remaining `violations[]` fail the test;
  `axe-incomplete` is still attached and still does not fail.
- `defineWcagContrastSpecs(manifest)` / `defineApcaContrastSpecs(manifest)` —
  kept as two functions, not one, for the same reason the original files were
  two specs: they answer different questions from different models, and
  either has to stay independently removable (see `specs/contrast-apca.ts`'s
  own header for what dropping APCA would touch)
- `defineVisualSpecs(manifest)` — one full-page screenshot per entry, per theme
- `SummaryReporter`, `seedTheme`/`THEMES`, and the APCA sampling utilities
- Shared `playwright.config.ts` fragments: the three viewport projects,
  `toHaveScreenshot` options, the standard CI-vs-local run flags, and a
  `createReporters()` helper

A consumer's own `.spec.ts` file is now three lines:

```ts
import { defineA11ySpecs } from "test-kit/specs/a11y";
import { pagesManifest } from "./pages.manifest";
defineA11ySpecs(pagesManifest);
```

`frontend-web` keeps its own `pages.manifest.ts` (the list of *its* pages);
`packages/storybook` keeps its own `story-manifest.ts` (generated from
Storybook's built `index.json`). Neither holds the checking logic, so neither
can drift from the other by someone editing one copy and not the other —
there is only one copy.

**One fix landed in the same move**: the axe tag list was missing `wcag22a`.
axe-core's WCAG version tags are not cumulative within a version — `wcag22aa`
only ever covered Level AA criteria added in 2.2, and 2.2 also added two Level
A criteria (3.2.6, 3.3.7) under a separate `wcag22a` tag, confirmed against
Deque's own tag table. Whatever rule axe registers under it was silently never
run before this.

**A later correction, not a second architecture.** The first draft of
`defineA11ySpecs` failed the build only on axe `critical`/`serious` and
attached everything else. That produced a successful CI job with 1458
structural findings — almost all `moderate` `landmark-one-main` and
`page-has-heading-one` on isolated Storybook stories, counted in the PR
comment and ignored by Playwright
([PR #7](https://github.com/yuriisurzhykov/Tallyvane/pull/7)). Wrapping every
story iframe in a fake `<main>`/`<h1>` would have silenced those two rules by
pretending each Button is a page; skipping the page-scoped list on
`surface: "component"` names the real mismatch instead. Remaining findings
fail. `frontend-web` stays `defineA11ySpecs(pagesManifest)` — missing `<main>`
or `h1` on a real page is now a failed test, which is the question those
rules actually ask.

## What did not move here

`pages.manifest.ts` (or its Storybook/admin equivalents) — that is each
consumer's own list of what to check, and is the entire reason this split
exists: the list stays local, the logic does not.

`frontend-web/scripts/contrast-table.ts` — a read-only tuning script, not part
of any check, that happens to also import `apca-w3` directly for its own
purposes. It stays in `frontend-web`, and `apca-w3` is declared as a
dependency in both places rather than routed through this package, since the
script has nothing else in common with a Playwright suite.

## Consuming it

```ts
import { defineA11ySpecs } from "test-kit/specs/a11y";
import { defineWcagContrastSpecs } from "test-kit/specs/contrast-wcag";
import { defineApcaContrastSpecs } from "test-kit/specs/contrast-apca";
import { defineVisualSpecs } from "test-kit/specs/visual";
import { VIEWPORT_PROJECTS, STANDARD_RUN_OPTIONS, STANDARD_USE_OPTIONS, SCREENSHOT_EXPECT_OPTIONS, createReporters } from "test-kit/playwright/shared-config";
```

`@playwright/test` is a peer dependency, not a regular one — see the comment
in `package.json`. A consumer must have its own `@playwright/test` at the
version this package's `peerDependencies` names; a second, independent copy
resolved only inside `test-kit` would register tests through a module instance
the consumer's own Playwright CLI never collects, which fails silently rather
than loudly.

## `src/cli/` — local iteration, not CI

CI already scopes its own cost: `visual-tests.yml`'s matrix runs one job per
consumer, and each job runs its whole suite exactly once. The problem these
two scripts answer is different — a person, at their own keyboard, iterating
on one component, re-running that same full suite (a `storybook build` of
2500+ modules, then several hundred contrast/a11y checks across two themes)
after every edit. That is real, measured local cost — CPU and wall-clock time
on the one machine that also has to keep being usable for everything else —
not a hypothetical one, and it is not something CI's own scoping touches at
all: CI runs on GitHub's machines, on a schedule the local developer does not
control and does not pay CPU for either way.

- **`run-scoped.mjs`** wraps `playwright test` with a guardrail: it refuses to
  run without `-g <pattern>` or an explicit `--all`, so "forgot to add a
  filter" cannot silently become "ran the full suite again." It also lowers
  `--workers` to 2 by default (still overridable) — more workers means more
  simultaneous Chromium processes, which is the opposite of what "go easy on
  this machine" asks for — and calls `free-ports.mjs` before and after, so an
  interrupted run, or a leftover from an earlier ad-hoc debugging session,
  never becomes the next run's silent problem.
- **`free-ports.mjs`** kills whatever is listening on a given list of TCP
  ports. By port, deliberately, not by matching a process name or command-line
  substring: this repo's own `webServer` configs set `reuseExistingServer:
  !process.env.CI` specifically so local iteration is fast, which is exactly
  what turns a manually-started debug server (spun up by hand to poke at one
  story in a browser, entirely outside any `pnpm run test:*` script) into an
  orphan nothing else ever notices — found running several of those side by
  side while diagnosing a real contrast failure live. A port is unambiguous
  in a way a name/command match is not: this project's own dev-server ports
  are a fixed, known list, so checking "is anything on port 6007" cannot
  mistake an unrelated process (an editor's own language server, say) for one
  of ours the way a text match against process names could on a machine
  whose exact process list this script cannot predict.
- **`reporters/compact-reporter.ts`** is `createReporters()`'s own local
  (non-CI) choice now, replacing `list`. `list` prints one line per test
  whether it passed or not — unreadable the moment a suite crosses a few
  hundred, which every suite in this package already does. This one prints
  nothing for a pass and exactly one line per failure
  (`file:line › title — reason`, ANSI-stripped so a captured log does not
  fill with raw escape codes); `SummaryReporter` still writes the full
  `test-results/summary.json` digest alongside it for anyone who needs more
  than one line, and the HTML report still has the rest.

Wired into `packages/storybook`'s and `frontend-web`'s own `test:scoped`
script — `pnpm run test:scoped -- <spec> -g "<pattern>"` — rather than a new,
separate npm script per consumer for each of the three concerns above: one
entry point is what actually makes "did you remember all three" not a thing
to remember.
