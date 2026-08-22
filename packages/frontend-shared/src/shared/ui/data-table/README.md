# data-table

Dense, virtualized, sticky-headed, keyboard-navigable grid with a slot for an
expanded row. Tier 1 — per `COMPONENTS.md` §4, **the single most
load-bearing component in the product**: the pipeline is a thousand rows at
16 ms a frame.

## What needed doing

`pipeline-table` (Tier 5, not built yet) needs a table that can hold on the
order of a thousand application rows without a scroll turning into a
frame-drop demo, with a sortable header, arrow-key navigation between
cells, and a per-row expansion slot for the widget's own detail content —
none of which a plain `<table>` gives for free once virtualization is the
only way to keep a thousand rows off the DOM at once.

## Why nothing existing could be reused instead

`ADR-031` names this exact gap when it settles on Base UI as the behaviour
layer for every other primitive in this package: "Тремя пробелами библиотека
не закрывает: таблица с виртуализацией (TanStack Table и Virtual, тоже
headless)..." — a virtualized table is explicitly *not* something Base UI
covers, unlike menus, popovers, comboboxes and every other compound already
built on it. `COMPONENTS.md` §11 separately rules out a second,
non-virtualized `Table` — "two table components would diverge, and the
dense one is the one under performance pressure" — so there was never a
lighter existing primitive to reach for first. `Collapsible` (Tier 0) is
reused directly for row expansion rather than a second disclosure
mechanism, per `COMPONENTS.md` §5's own line: "Row expansion — `Collapsible`
inside a `DataTable` row."

## What was actually done

`Root`/`Header`/`Body`/`Row`/`Cell`, per `component-authoring/SKILL.md` §3.2
— `DataTable` is one of the two components that skill names by example as
warranting a compound API. `Root` calls `useTable` (TanStack Table) and
`useVirtualizer` (TanStack Virtual) once and exposes both, plus this
component's own bespoke state (row expansion, the roving-focus cell
position), through a single `state`/`actions`/`meta` context
(`patterns.md` §2) — every other part reads that context and never touches
either raw hook.

### TanStack Table v9, not the v8 most tutorials still describe

`@tanstack/react-table@9.1.2` and `@tanstack/react-virtual@3.14.10`, exactly
the pair the plan named. v9 (shipped August 4, 2026) is a from-scratch
rewrite with a materially different shape than v8 — confirmed by reading the
installed package's own bundled `skills/` directory (`getting-started`,
`table-state`, `migrate-v8-to-v9`, `with-tanstack-virtual`) rather than
guessed from training data, since the package explicitly ships these for
exactly this reason. The two changes that actually mattered here:

- **`useTable`, not `useReactTable`**, and every optional capability
  (sorting, column sizing) is an explicitly registered `tableFeatures()`
  slot rather than bundled by default. This file registers exactly
  `rowSortingFeature` + `createSortedRowModel()` (the sort itself) and
  `columnSizingFeature` (for `size`/`getSize()` only — see the flex-sizing
  decision below). It deliberately does **not** register
  `rowExpandingFeature`: the plan is explicit that row expansion is this
  component's own state composing `Collapsible`, not TanStack's built-in
  expanded-row-model feature, so registering it would add a second,
  unused expansion mechanism sitting next to the real one.
- **`ColumnDef` now takes a `TFeatures` type parameter**
  (`ColumnDef<TFeatures, TData, TValue>`). The task asks for TanStack's own
  `ColumnDef` "re-exported... rather than redefined" — the closest literal
  reading would be re-exporting the raw three-parameter generic, but that
  would force every caller to also import `tableFeatures`/
  `rowSortingFeature`/`columnSizingFeature` just to name a column, which is
  exactly the raw-hook leakage the compound contract exists to prevent.
  `DataTableColumnDef<TData, TValue = unknown>` fixes `TFeatures` to this
  module's own internal registration instead — the type's *shape* is
  untouched, only one otherwise-unavoidable generic parameter is resolved
  on the caller's behalf. Flagged here as the one place "re-export, don't
  redefine" needed a judgment call.

### Virtualization, and why `measureElement` stays wired even though row height is fixed

`useVirtualizer<HTMLDivElement, HTMLDivElement>` with `estimateSize: () =>
ROW_HEIGHT_PX` (32, matching `--control-height-sm` — see the density note
below) and `ref={virtualizer.measureElement}` on every row's outer wrapper.
The two are not redundant: `estimateSize` alone would be sufficient for a
row that is *always* exactly `ROW_HEIGHT_PX` tall, but a row with
`renderExpandedRow` open is genuinely taller (its cells strip plus the
`Collapsible.Panel` below it) — `measureElement`'s `ResizeObserver` is what
lets the virtualizer discover that real height and reflow every row below
it. A real, visible interaction worth stating plainly: `Collapsible`'s own
open/close height transition fires that same `ResizeObserver` on every
intermediate animation frame, so rows below an expanding/collapsing row
visibly shift position while the transition plays. That is a genuine
interaction between the two libraries, not a bug, and not one this
component fixes by fighting `Collapsible`'s own established transition —
noted here rather than silently shipped.

### Why `Header` is not literally `position: sticky`, and why columns never overflow horizontally

The task's own required shape makes `Body` "the actual scroll/virtualization
container" — its own, separate element from `Header`. The more common
"single scrollbar" virtualized-table recipe puts header and body rows in
*one* scroll container and pins the header with `position: sticky; top: 0`,
which also keeps the header in horizontal sync with body columns for free.
With `Header` and `Body` as two separate siblings (as specified), that
recipe does not apply: `Header` never scrolls in the first place, so
`position: sticky` buys nothing, and it would reintroduce a real question —
Base UI's own `z-index` roles (`sidebar`, `popover`, `modal`, `toast`,
`tooltip`) name overlay layers or page-level persistent chrome, and none of
them is "a non-overlay header pinned above a sibling scroll region," so
pinning it would need either a new named role or a judgment call between two
imperfect existing ones.

Resolved by removing the need for either: `Header` is simply outside the
scrolling region (a plain, non-positioned flex sibling above `Body`), and
every column sizes by **flex proportion** — `style={{ flex: `${getSize()} 1
0%` }}` on both a header cell and every body cell in that column — rather
than an absolute pixel width. Every row is therefore always exactly 100% of
the container's own width, so there is no horizontal overflow for `Header`
and `Body` to ever fall out of sync over. The real cost of this choice:
there is no horizontal scroll and no interactive column resize in this
pass. `columnSizingFeature` is registered only for its static `size` field
(the relative flex weight) and `getSize()`; `columnResizingFeature`
(drag-to-resize) is not registered — no named consumer needs it yet
(`Rule of Three`), and adding it later is additive to this file, not a
breaking change to its public API.

### Keyboard navigation — a real, hand-rolled roving-tabindex grid

`ADR-031`'s own gap list already names "a virtualized table" as something
Base UI does not cover; the same is true of a 2-D cell grid's keyboard
model specifically (Base UI's own primitives handle 1-D roving lists —
`Menu`, `Select` — not a row/column grid). `Cell` and the expand-toggle
button each carry `tabIndex={0}` only while they are `state.activeCell`,
`-1` otherwise, matching the standard WAI-ARIA "grid" pattern:

- **Arrow keys** move the active position by one row or column.
- **Home/End** jump to the current row's first and last column — row-scoped,
  per the task's own wording, not a whole-grid `Ctrl+Home`/`Ctrl+End`. Not
  built in this pass; a real scope cut, not an oversight, since nothing in
  the required shape asked for it.
- Real DOM focus does not follow a `tabIndex` change on its own — the
  browser leaves focus exactly where it was until something calls
  `.focus()`. Each `Cell`/toggle button owns a small `useEffect` that calls
  `.focus()` on itself the moment it becomes the active cell; calling
  `.focus()` on an already-focused element (the mouse-click path, which
  focuses natively before this ever runs) is a harmless no-op, so one code
  path covers both origins.
- Moving to a row that is not currently mounted (scrolled out of the
  virtualizer's range) calls `virtualizer.scrollToIndex` first, so the
  target row mounts before the focus effect above has anything to focus.
- Sortable headers are deliberately **not** part of this roving system —
  each renders a real `<button>` (a normal, always-present tab stop;
  `Enter`/`Space` activate it natively). The task's own wording ("arrow keys
  move a roving focus position *between cells*") describes `Body`'s cells,
  and mixing two structurally different row types (header vs. body) into
  one roving sequence would have doubled the keyboard-math surface for no
  named requirement.

### Row expansion's leading toggle column

When `renderExpandedRow` is supplied, `Row` prepends one synthetic
leading cell — a `Collapsible.Trigger` with a `ChevronRight`/`ChevronDown`
icon — before the caller's own columns, and `Header` prepends a matching
blank `gridcell` so every row's column count stays aligned. It is not a
`columnheader`: this column has no heading, and marking an empty cell as
one fails axe `empty-table-header`. That rule also rejects `aria-label` on
an otherwise empty header as the markup that still leaves the header
blank. `aria-colcount` and every `aria-colindex` account for it.

This was a real design choice: the
alternative was making the caller build their own "expand" column via a
normal `cell` render function, but the task's own wording — "compose
`Collapsible.Root`/`Panel` around the caller's `renderExpandedRow(row)`
output" — reads as a `Root`-level opt-in, not a per-column concern, and a
caller building their own expand affordance would need to duplicate this
component's own state (`state.expandedRowIds`) to render it correctly
anyway. `Collapsible.Root`'s `open`/`onOpenChange` are fully controlled by
that state — never `Collapsible`'s own internal uncontrolled mode — per the
task's own "track this as real component state, exposed via the context."

`expandRowLabel`/`collapseRowLabel` are **required** props whenever
`renderExpandedRow` is given (a discriminated union makes the invalid
half-supplied combination a type error, the same `SKILL.md` §3.4 pattern
`Toggle`'s controlled/uncontrolled split already uses) — this component
holds no domain copy of its own (`COMPONENTS.md` §12), so the toggle's
accessible name has to come from the caller, the same convention
`IconButton.label` and `MultiSelect.ChipRemove.label` already establish for
every icon-only control in this package. `Root`'s own `aria-label` is
required for the identical reason, at the whole-grid level.

### Density — a stated scope cut, not a silent omission

`COMPONENTS.md` §5 states plainly that density tokens are not built yet.
This ships exactly one fixed row height (`ROW_HEIGHT_PX = 32`) rather than a
`data-density` attribute with nothing yet to switch. The number is not
arbitrary: it equals `--control-height-sm` (`dimension.8`, `2rem`) at the
default root font size — the same height role `Select`'s `sm` trigger
already uses, chosen because "dense" is this component's own explicit
requirement. Painted row height is `var(--control-height-sm)`. The JS
number `ROW_HEIGHT_PX` exists only for `useVirtualizer.estimateSize`,
which cannot read a CSS variable — [ADR-042](../../../../../../docs/adr/ADR-042-virtualizer-row-height-js-pixels.md).
A named rem constant is no longer a lint exemption. Header sort glyphs use
`h-(--control-icon)` rather than Lucide `size={14}`. `Grid.tsx`'s `columns`
prop remains a count, not a CSS length. Real CSS/JS drift (a non-default
root font size, browser zoom) is
corrected automatically the moment a row actually mounts, via the same
`measureElement` wiring described above.

## `data-virtualized-scroll-container`, and the axe false positive it opts out of

`Body`'s scroll div carries this boolean marker attribute alongside its
`data-testid`. It exists for one external reader: `test-kit`'s
`contrast-wcag.ts` walker, which clears an axe `color-contrast` "incomplete"
result (`messageKey: "elmPartiallyObscured"`) only when the node's ancestor
carries this exact attribute *and* the node's own `getBoundingClientRect()`
falls outside it — i.e. an overscan row the virtualizer keeps mounted past
the visible fold, which `elementFromPoint`-based occlusion detection
misreads as "covered by something else" because sampling its center falls
through the container's own clip. Deliberately a marker this component
opts into, not a generic "any clipped scrollable ancestor" rule, so it can
never silently start applying to an unrelated `ScrollArea` or menu listbox
that merely happens to scroll.

## Testing strategy, and the real gap it works around

`@testing-library/react`/jsdom never computes real layout: `offsetWidth`/
`offsetHeight` are always `0`. `@tanstack/react-virtual`'s own default
`observeElementRect` reads exactly those two properties on the scroll
element to decide the viewport's size — with jsdom's real, always-zero
values, `calculateRange` bails out (`outerSize === 0`) before ever computing
a visible window, and `getVirtualItems()` returns nothing to query against.

`DataTable.test.tsx` stubs `HTMLElement.prototype.offsetHeight` with a
`get()` that returns a real, non-zero height *per element* — `600` for
`Body`'s own scroll container (found via its own `data-testid`, the same
`data-testid="scroll-area-viewport"` convention `ScrollArea.tsx` already
established for "the real scrollable element"), and `32`
(`ROW_HEIGHT_PX`) for everything else, so every row's `measureElement` call
reports a size consistent with the fixed row height this component actually
renders. A single blanket value (the shape `ScrollArea.test.tsx`'s own
`scrollHeight`/`scrollWidth` stub uses) does not work here the way it does
there: `ScrollArea` has exactly one scrollable element to fake a size for,
while this component has one *tall* element (the viewport) and many *short*
ones (the rows) that both read the same DOM property with different correct
answers. This is the "own exported test utility" surface the task pointed
at in spirit — `observeElementRect`/`estimateSize`/`measureElement` are all
real, overridable/measured `VirtualizerOptions`, not internals — applied at
the one DOM property their real, unmodified implementations actually read,
rather than overriding those options directly (which would have meant
threading a test-only escape hatch through `Root`'s own public props for a
concern real production code never needs).

Vitest covers: column rendering (including a repeated cell value, asserted
with `getAllByText` rather than a false assumption of uniqueness), sorting
(ascending/descending via `aria-sort`, and a non-sortable column's affordance
omitted entirely rather than stated as `"none"`), the full keyboard contract
(roving `tabindex`, arrow keys, Home/End, edge clamping, click-to-activate),
and row expansion (the toggle's label/`aria-expanded` flipping, the caller's
content becoming queryable, collapsing again). What it cannot cover — real
scroll-position math against a genuinely large dataset — is exactly what
`DataTable.stories.tsx`'s `LargeVirtualizedDataset` story (1000 rows) is for,
verified by opening it in a real browser rather than assumed correct. A
follow-up Playwright spec asserting on scroll position after a keydown (the
same hand-written pattern `drawer-keyboard.spec.ts` already establishes for
`Drawer`'s own load-bearing behaviour) is the next real step for that claim
— out of scope for this pass, not forgotten.

## Why it's understandable, scalable, extensible

Understandable: five parts, each with one job stated in its own doc
comment, all reading the same three-shape context rather than five
different ad hoc prop chains. Scalable: the entire reason for existing —
only the rows `useVirtualizer` currently measures as visible ever mount, so
`pipeline-table`'s thousand rows cost the same DOM size as ten. Extensible:
`columnResizingFeature`, `rowPinningFeature`, a `data-density` attribute
once its tokens exist, or a genuinely dynamic (not just expansion-driven)
row height are all additive registrations/props on `Root`, never a rewrite
of `Header`/`Body`/`Row`/`Cell`'s own contract with it.

## SOLID

**Single responsibility.** `Root` owns model construction and the
`state`/`actions`/`meta` contract; `Header` only renders header groups and
their sort affordances; `Body` only owns the scroll/virtualization
container; `Row` only lays out one row's cells and its optional expansion;
`Cell` only renders one value and its roving-focus bookkeeping. **Open/
closed.** A new capability (pinning, resizing) is a new registered feature
plus new context fields — no existing part's rendering logic branches on
it. **Liskov substitution.** Every part depends on the `state`/`actions`/
`meta` interface, never on `useTable`/`useVirtualizer` directly — a future
provider that sources rows from somewhere else entirely (a different
`Root` implementation) would leave `Header`/`Body`/`Row`/`Cell` unchanged,
the same guarantee `patterns.md` §2 states for every compound in this
package. **Interface segregation.** `DataTableRootProps`'s expansion props
are a discriminated union, not three independently-optional fields — a
caller who does not use row expansion sees and supplies nothing about it.
**Dependency inversion.** Every part imports `useDataTableContext`, never
`@tanstack/react-table`/`@tanstack/react-virtual` themselves — the one file
that would need to change if either library's API changed shape again (as
v8→v9 already did once) is `DataTable.tsx` itself.
