---
name: Testing coverage gaps
overview: "Close the automatable testing gaps found while auditing the accessibility suites: the frontend has no unit tests at all, and six WCAG-adjacent behaviours that Playwright can verify are unverified."
todos:
  - id: vitest-setup
    content: "Set up Vitest in frontend: vitest, jsdom, @testing-library/react; add the test script; add the matchMedia stub"
    status: pending
  - id: provider-tests
    content: "Unit-test the theme provider: server snapshot, stored preference, system following, class application, setPreference"
    status: pending
  - id: depcruise-tests
    content: Exclude test files from dependency-cruiser's no-orphans rule
    status: pending
  - id: reduced-motion
    content: "Spec: reduced motion collapses transitions, and does not when not requested"
    status: pending
  - id: keyboard
    content: "Spec: focus is visibly indicated and tab traversal has no trap"
    status: pending
  - id: reflow
    content: "Spec: reflow at 320px (ask first whether a desktop console must satisfy it)"
    status: pending
  - id: text-spacing
    content: "Spec: WCAG text-spacing overrides cause no clipping"
    status: pending
  - id: resize-text
    content: "Spec: doubled root font size causes no overflow"
    status: pending
  - id: forced-colors
    content: "Optional spec: page stays legible under forced colours"
    status: pending
  - id: reporter-incomplete
    content: Digest axe-incomplete into the run summary and the pull-request comment
    status: pending
isProject: true
---

# Closing the automatable testing gaps

## Why

An audit of the accessibility suites established two things. Coverage by `axe-core` is effectively complete — 100 of its 105 rules are requested, the remaining five being three AAA rules that no law requires and two the engine marks obsolete. But `axe` cannot test behaviour that requires resizing, keyboard interaction or media emulation, and none of that is covered.

Separately and more seriously: **the frontend has no unit tests.** `frontend/package.json` has no `test` script, and `frontend/src` and `frontend/app` contain zero test files. The root `pnpm test` fans out with `--recursive --if-present`, so it currently runs the token engine's 225 tests and nothing else. The most intricate code we have written — the theme provider's hydration handling — rests on nobody having checked it.

Work in the order below. The first item is the largest and the most valuable; the rest are small and independent of each other.

## 1. Unit tests for the theme provider

The subject is [frontend/src/shared/ui/theme/provider/theme.context.tsx](../../frontend/src/shared/ui/theme/provider/theme.context.tsx), which uses `useSyncExternalStore` with a deliberately different server snapshot. That is exactly the shape that fails silently: a hydration mismatch does not throw, it renders the wrong theme for one frame.

Set up Vitest in `frontend`, matching the engine package's version (`vitest@^4`), plus `jsdom`, `@testing-library/react` and `@testing-library/dom`. Import `describe`/`it`/`expect` explicitly rather than enabling globals, so no ESLint environment changes are needed.

Add `"test": "vitest run"` to `frontend/package.json`. The root `pnpm test` then picks it up with no change to the root script.

What to cover:

- The server snapshot is dark, on both server and client's first render. Render with `renderToString` and assert the markup matches a client hydration render — this is the mismatch guard, and it is the whole reason the provider is written the way it is.
- With nothing stored, the preference resolves to `system` and the theme follows `prefers-color-scheme`.
- With a stored preference, that wins over the system.
- `setPreference` writes to `localStorage` and updates the theme.
- The root element's class list ends up with exactly one of `theme-dark` / `theme-light`, never both.
- A system change while on `system` flips the theme; a system change after an explicit choice does not.

Two gotchas the executing agent will hit:

- **jsdom does not implement `matchMedia`.** A stub is required in the test setup, and it must support `addEventListener("change", …)` because the provider subscribes through it. Do not stub it as a no-op returning `matches: false` only — half the cases above then cannot be exercised.
- **`useTheme` throws outside a provider.** That is intentional (see [use-theme.ts](../../frontend/src/shared/ui/theme/provider/use-theme.ts)); assert it rather than working around it.

### Consequence for the dependency graph

Test files placed next to the source will be reported by `dependency-cruiser`'s `no-orphans` rule — nothing imports them. Add an exclusion to the `pathNot` list in [frontend/.dependency-cruiser.cjs](../../frontend/.dependency-cruiser.cjs) alongside the existing ones, with a comment saying why (a test is an entry point, not dead code).

## 2. Reduced motion is actually honoured

[frontend/src/shared/ui/theme/adapters/tailwind.css](../../frontend/src/shared/ui/theme/adapters/tailwind.css) ends with a `@media (prefers-reduced-motion: reduce)` block that collapses every transition and animation to 1ms. Nothing verifies it.

New spec, `frontend/tests/e2e/reduced-motion.spec.ts`. Playwright supports the emulation — `reducedMotion` appears in `playwright-core`'s type definitions. Use `test.use({ reducedMotion: "reduce" })`, then assert the computed `transition-duration` on an element carrying one of the `transition-*` utilities is at most 1ms. Assert the opposite under `reducedMotion: "no-preference"`, so the test proves the media query is doing the work rather than the value being 1ms unconditionally.

## 3. Focus is visible, and the keyboard can traverse the page

`axe` checks focus semantics but not whether a focus indicator is actually drawn. The design system ships a `focus-ring` utility, and nothing asserts it reaches anything.

New spec, `frontend/tests/e2e/keyboard.spec.ts`:

- Press `Tab` repeatedly, collecting `document.activeElement` at each step. Assert focus advances, returns to the document after the last control, and never revisits a single element forever — that is the keyboard trap check, SC 2.1.2.
- For each focused element, assert its computed `outline-width` or `box-shadow` differs from its unfocused state. Comparing against the unfocused state matters: asserting "outline is not none" passes on a browser default outline that the design may have removed elsewhere.

## 4. Reflow at 320px — SC 1.4.10 AA

New spec, `frontend/tests/e2e/reflow.spec.ts`, with `test.use({ viewport: { width: 320, height: 640 } })`. Assert `document.documentElement.scrollWidth <= clientWidth`, so content reflows rather than demanding two-dimensional scrolling.

**Ask before implementing.** The product is a console with a 1280px content width and a 1600px exception for the brief ([docs/frontend/01-shared-design-tokens.md](../../docs/frontend/01-shared-design-tokens.md) §9.3). Whether a desktop console must satisfy 320px reflow is a product decision, not a technical one. The criterion says yes; the design may disagree. Do not silently pick.

## 5. Text spacing — SC 1.4.12 AA

New spec, `frontend/tests/e2e/text-spacing.spec.ts`. Inject the overrides the criterion specifies via `page.addStyleTag`: line height 1.5x font size, paragraph spacing 2x, letter spacing 0.12em, word spacing 0.16em. Then assert no element's `scrollWidth`/`scrollHeight` exceeds its `clientWidth`/`clientHeight` — clipping is the detectable half of "no loss of content".

State the limit in the file: overlap and truncation that does not produce overflow are not caught. Half a check that says so beats a whole one that implies more than it does.

## 6. Resize text — SC 1.4.4 AA

New spec, `frontend/tests/e2e/resize-text.spec.ts`. Double the root font size and assert no horizontal overflow and no clipping. Doubling the root size, rather than zooming the viewport, is what the criterion is about — text scaling independently of the layout.

This is where the `rem`-based scale in [tokens/dimension.ts](../../frontend/src/shared/ui/theme/tokens/dimension.ts) gets tested for the first time; if anything is pinned in pixels that should not be, this finds it.

## 7. Optional: forced colours

`forcedColors` is also in Playwright's types. Windows High Contrast replaces the palette wholesale, and a design that leans on background colour for meaning — status washes, the selected-row surface — can lose that meaning entirely. A spec asserting the page stays legible under `forcedColors: "active"` is cheap. Lower priority than the rest.

## Wiring, and one thing to notice

New specs are picked up by `test:e2e` automatically. They will run under the `desktop` project only, because the `tablet` and `mobile` projects in [frontend/playwright.config.ts](../../frontend/playwright.config.ts) carry `testMatch: /visual\.spec\.ts/`. That is correct for all of these — each sets whatever viewport it needs.

Also worth doing while in this area: the structural suite attaches `axe-incomplete` (results `axe` could not decide) but the summary reporter in [frontend/tests/e2e/reporters/summary-reporter.ts](../../frontend/tests/e2e/reporters/summary-reporter.ts) does not digest it, so those unknowns never reach the pull-request comment. Add a count, the way `wcagContrastUnmeasuredCount` was added for the contrast suite.

## Definition of done

`pnpm verify` stays green, and `pnpm --filter tallyvane-frontend run test:e2e` reports the new specs. The contrast suites will still fail — the palette has not been fixed yet, and that is separate work.

Every new spec must be shown to fail when the thing it checks is broken. Temporarily break it, watch the failure, restore. A check that has never failed has not been tested; that mistake has already been made twice in this repository, once with a dependency rule written against a path that pnpm never produces, and once with a contrast rule that treated unmeasurable text as passing.
