---
name: component-authoring
description: >-
  Guidance for designing and writing reusable React components — API design,
  composition, TypeScript patterns, performance under React Compiler,
  accessibility, file layout, and SOLID/DRY/YAGNI/KISS applied to components.
  Use when creating, reviewing, or refactoring a React component in
  frontend-web, frontend-admin, packages/frontend-shared, or packages/content-kit,
  or when deciding a component's props or composition shape.
---

# Component authoring

Full code examples for every rule below live in
[patterns.md](patterns.md) — read it when implementing, not just skimming.

## 1. Baseline facts (2026)

- **React 19**: `ref` is a normal prop; `forwardRef` is optional and slated
  for deprecation. `use(Context)` replaces `useContext` and, unlike it, can be
  called conditionally.
- **React Compiler 1.0** (stable since October 2025, stable opt-in in
  Next.js 16): automatic memoization at build time. For new code, do not add
  `useMemo` / `useCallback` / `React.memo` by default — write plain code that
  follows the Rules of React and let the compiler optimize it. Both
  `frontend-web/next.config.ts` and `frontend-admin/next.config.ts` exist (for
  `transpilePackages` across the new workspace packages) but neither sets
  `reactCompiler: true` yet — treat enabling it as an open decision, not
  something to assume either way.
- **Base UI's `render` prop** (not `asChild`/`cloneElement`) is this
  project's composition and polymorphism mechanism, per
  [ADR-031](../../../docs/adr/ADR-031-base-ui-as-behaviour-layer.md) — explicit,
  typed via the function-form arguments, and RSC-safe in a way `cloneElement`
  is not.

## 2. The one axis that decides reuse

What promotes a component up the ladder in
[`packages/frontend-shared/src/shared/ui/COMPONENTS.md`](../../../packages/frontend-shared/src/shared/ui/COMPONENTS.md)
§1 is not size, it is how much the component knows. Before writing anything,
place it on that ladder. A Tier-0/1 component that suddenly needs a domain
noun or a piece of copy is actually a Tier-3-or-above component that got
started in the wrong package — move it, don't give `frontend-shared` an
exception.

Per [ADR-032](../../../docs/adr/ADR-032-subdomain-split-and-admin-isolation.md),
the ladder now spans package boundaries, not just directories in one app:
Tier 0–2 (primitives, compounds, layouts) live in `packages/frontend-shared`;
Tier 3–5 for the console and public site live in `frontend-web/src`; the same
tiers for the admin surface live in `frontend-admin/src`; and the
content-domain slices specifically (`content-page`, `media-asset`,
`block-renderer`, `*-block` widgets) live in `packages/content-kit` instead of
`frontend-web/src`, because that domain is shared between the public site and
admin. Which package a component belongs to is decided by the same knowledge
test as which tier — a component doesn't move to `frontend-shared` because
it's convenient to share, it moves there because it has zero business
meaning.

## 3. Core API rules

Each rule: problem → rule → pointer to the full example in `patterns.md`.

**3.1 Don't multiply boolean props — compose.** `isThread`, `isEditing`,
`isDMThread`... N booleans is 2^N states, almost all invalid, and unreadable
at the call site. Replace with explicit variant components that each compose
only what they need. See [patterns.md §1](patterns.md#1-explicit-variants-over-boolean-props).

**3.2 Compound components for real structural variability.** Reach for a
Context-backed compound API (`Root` / `Trigger` / parts) only when a
component is genuinely drowning in configuration — `DataTable`, `Board`,
`ActionMenu`, `CommandPalette` all qualify. Two or three stable props with no
structural variation stays a plain component; wrapping that in a compound API
is pure ceremony. Split the context value into `state` / `actions` / `meta`
so the UI parts depend on that interface, never on a concrete hook — the
provider is the only place that knows whether state is local, global, or
server-synced, and shared state's boundary is the provider, not the JSX
nesting. Name parts the way Base UI already does in this codebase —
`DataTable.Root`, not `DataTable.Provider` — for consistency with the Base UI
primitives sitting in the same tree. See
[patterns.md §2](patterns.md#2-compound-components-with-a-context-contract).

**3.3 Polymorphism via `render`, not children-cloning.** Follow the
`useRender` shape: a `render` prop that accepts an element or a
`(props, state) => ReactNode` function, typed via `ComponentProps` (public)
vs `ElementProps` (internal). Changing the rendered tag needs an explicit
signal for props only valid on the default tag (e.g. `nativeButton={false}`)
— the component cannot detect the target element before hydration. See
[patterns.md §3](patterns.md#3-render-prop-polymorphism).

**3.4 Controlled/uncontrolled via discriminated unions.** Never make both
`value` and `defaultValue` independently optional — use `never` on the
forbidden fields of each union member so the invalid combination is a type
error, not a runtime footgun. Before writing a local
`useControllableState`-style hook, check whether the installed `@base-ui/utils`
already exports a `useControlled` equivalent — it is documented in Base UI's
internal `ReactStore` utilities, but whether it is a stable public export in
the version pinned here has not been verified. Don't add a second dependency
(e.g. `@radix-ui/react-use-controllable-state`) for something already inside
an existing one without checking first. See
[patterns.md §4](patterns.md#4-controlled-uncontrolled-state).

**3.5 `children` for structure, render props only when data flows back.**
Prefer `children` composition over a wall of `renderHeader` / `renderFooter`
/ `renderActions` props. Use a render prop only when the parent must hand
data back to the caller — a list's `renderItem({ item, index })`, or
`DataTable`'s row/column definitions. See
[patterns.md §5](patterns.md#5-children-vs-render-props).

**3.6 TypeScript keeps the API honest.** Separate a component's public props
type from the internal element props it forwards. Use exhaustiveness
checking (`default: never` in a `switch`) on every prop that discriminates a
variant, so a forgotten case is a compile error, not a silent no-render.
Never assign `undefined` to an optional field under `exactOptionalPropertyTypes`
— use conditional spreading. Add a type parameter only when a caller genuinely
needs it (e.g. a generic `renderItem`), not by default. See
[patterns.md §6](patterns.md#6-typescript-prop-shapes).

## 4. SOLID, DRY, YAGNI, KISS applied to components

**S — Single Responsibility.** If you cannot describe what the component
does in one sentence without "and", split it. In practice this means
extracting data-fetching and business logic into a hook (the container) and
leaving the component a pure presenter (props in, JSX out). This is exactly
what the ladder in `COMPONENTS.md` already enforces structurally: Tier 0/1
never fetches or mutates, Tier 3 reads, Tier 4 writes.

**O — Open/Closed.** Extend behavior through composition (`children`, the
`render` prop, an added compound part) — never by adding another conditional
branch inside a component that is otherwise stable. If shipping a new use
case means editing an existing component's body, OCP has already been
violated.

**L — Liskov Substitution.** A compound sub-part must honor its parent's
contract regardless of who implements it — `Composer.Submit` behaves the same
whether the provider behind it is a local-state form or a server-synced one
(see 3.2). If swapping the provider changes what a child part does, the
context interface leaked an implementation detail.

**I — Interface Segregation.** A component should ask for only the props it
uses. A component forced to accept a large prop bag "for all callers" instead
of a focused one is a signal to split it, not to make more of the props
optional.

**D — Dependency Inversion.** UI components depend on the context interface
(`state`/`actions`/`meta`), never on a concrete state implementation
(`useState`, a global store, a server hook). The provider is the one place
allowed to know which.

**DRY has a caveat that matters here: DRY without S is a trap.** A shared
component is only safe to reuse while it stays purely presentational. The
moment domain logic gets baked into a "reusable" component, reusing it across
domains becomes the bug, not the win — which is exactly why `shared` holding
no domain types (already a project rule, `COMPONENTS.md` §2) is not
incidental. If two call sites need slightly different domain behavior around
the same visual shape, that is a sign for two Tier-3+ components composing
one shared Tier-0/1 primitive, not one primitive with a `variant` prop that
encodes domain meaning.

**YAGNI / Rule of Three.** Don't generalize a single component beyond what
2–3 known call sites actually need — abstracting on one example is a guess,
not foresight. Be honest about the tension with this repo's own process:
`COMPONENTS.md` plans roughly 150 components before the first screen exists,
which is a deliberate exception at the *planning* level ("rails before the
first module"). That does not license skipping Rule of Three at the
*implementation* level of any single one of them — `DataTable` should be
built as generic as its 2–3 known consumers (pipeline table, board) actually
require, not as generic as it could theoretically be.

**KISS.** Don't abstract syntax, abstract behavior — a wrapper around a
`<div>` that only renames a prop doesn't earn its cost (one more thing to
document, test, and remember). One concept, one API: if HTML semantics
already own something (`<h2>`), or an existing token component already owns
something (`Text` owns typography), don't let a new component re-solve it.

## 5. Performance under React Compiler

Don't reach for manual memoization by default in new code. It remains
legitimate, as a measured exception, in three cases:

1. A third-party imperative API needs a stable reference —
   `DataTable`'s TanStack Virtual dependency is exactly this case.
2. A value is a required `useEffect` dependency.
3. Profiling has identified an actual hot path the compiler did not cover.

Use `eslint-plugin-react-compiler` to catch the patterns (mutating props,
reading a ref during render, `setState` in an effect) that make the compiler
silently skip a component, rather than guessing why a component isn't
optimized.

## 6. Accessibility

Reuse Base UI's behavior for anything with real interaction machinery —
focus return, roving tabindex, typeahead, edge-collision flip, portal,
trigger-click vs outside-click. Hand-rolling reproduces bugs that are already
fixed upstream and are invisible to visual tests. Verify, don't assume: the
three CI suites (structural, WCAG, APCA) already required by this repo's
methodology are the bar, and `incomplete` in axe is not a pass.

This is a volume/frequency threshold, not an absolute — see
[patterns.md §7](patterns.md#7-the-volumefrequency-threshold--when-not-to-reach-for-base-ui)
for two real, tested examples of where hand-rolling the same ARIA patterns
was the right call at a smaller scale, and why the same reasoning flips at
this project's scale.

## 7. File and public-API conventions

One directory per component, `index.ts` as its only public surface (already
`COMPONENTS.md` §12). A wrapper component that only renames a prop over its
base component is not earning a place on the ladder — either compose it at
the call site, or improve the base component's API instead of adding a layer.

Every component directory also gets a live `README.md` — updated the moment
something happens during the work, not composed once at the end. Structure
and tone follow the workspace `development-methodology` rule §3: what the
component is and what problem it solves, **why nothing existing could be
reused instead**, what was actually done (wrong turns recorded as they're
corrected, not erased), why it's understandable/scalable/extensible, and the
SOLID angle as its own heading. `COMPONENTS.md`'s row for the component stays
a short pointer to this file — one home per fact, not a second copy of the
same prose that can drift from the first.

## 8. Anti-patterns (mirrors `COMPONENTS.md` §11 — don't relitigate these)

- A generic `Box` with style props — a hole in token discipline shaped like
  convenience.
- A second colour vocabulary on a component (`color` instead of `tone`).
- Component-level theme overrides — defeats the token audit.
- A second, non-virtualized `Table`.
- A component with a dozen boolean/mode props — refactor into a compound API
  or explicit variants (3.1/3.2) instead of adding a thirteenth.
- Hand-rolled accessibility machinery where a headless equivalent exists.

## 9. What's already decided here vs. still open

| Rule | Already decided | Still open |
| --- | --- | --- |
| Knowledge = the reuse axis | `COMPONENTS.md` §1, the Tier ladder | — |
| Polymorphism via `render` | ADR-031, `COMPONENTS.md` §12 | — |
| Compound part naming | Base UI ships `Root`/`Trigger`/`Popup` | This project's own Tier-1 compounds (`DataTable`, `Board`, `ActionMenu`) aren't written yet — should mirror that naming |
| Controlled/uncontrolled utility | `@base-ui/react`/`@base-ui/utils` is already a dependency | Whether the installed version publicly exports a `useControlled` equivalent — verify before adding a new hook or dependency |
| React Compiler | `next.config.ts` exists in both apps (for `transpilePackages`) | Neither sets `reactCompiler: true` — undecided config change |
| `className` passthrough | Layout-only, enforced by lint rules (`COMPONENTS.md` §11) | — |
| Copy as props below Tier 3 | `COMPONENTS.md` §12 | — |

## 10. Checklist before calling a component done

- [ ] Placed correctly on the Tier ladder — no domain noun or copy below Tier 3.
- [ ] One sentence, no "and", describes what it does (SRP).
- [ ] No boolean prop you'd have to explain in a code review — composition or
      explicit variants instead.
- [ ] If compound: parts depend on the context interface, not a concrete hook.
- [ ] If polymorphic: uses `render`, typed via public/internal prop split.
- [ ] If stateful: controlled/uncontrolled modes are mutually exclusive at
      the type level, not just by convention.
- [ ] Built for the 2–3 call sites that actually exist, not for a guessed
      future one (YAGNI).
- [ ] No manual `useMemo`/`useCallback`/`React.memo` without a stated,
      measured reason.
- [ ] Every interactive part is reachable, operable, and visible by keyboard
      — verified, not assumed.
- [ ] One directory, `index.ts` is the only import path used by callers.
- [ ] `README.md` created and updated live during the work (workspace
      `development-methodology` rule §3) — not written retrospectively after
      the fact. `COMPONENTS.md`'s row points to it rather than duplicating it.
