# Component inventory

What exists, what will exist, and — the part that actually decides arguments —
**which rung of the ladder a thing belongs on and why.**

This is a plan, not a report. Almost nothing here is built yet; the design
system beneath it is (`theme/`). The inventory is written now, before the first
screen, because the alternative is discovering the ladder retroactively from
whatever got written first.

## Contents

1. [The ladder, and what moves a component up it](#1-the-ladder-and-what-moves-a-component-up-it)
2. [Constraints that shape every entry](#2-constraints-that-shape-every-entry)
3. [Tier 0 — Primitives](#3-tier-0--primitives)
4. [Tier 1 — Compounds](#4-tier-1--compounds)
5. [Tier 2 — Layouts](#5-tier-2--layouts)
6. [Tier 3 — Entity components](#6-tier-3--entity-components)
7. [Tier 4 — Feature components](#7-tier-4--feature-components)
8. [Tier 5 — Widgets](#8-tier-5--widgets)
9. [Tier 6 — Views](#9-tier-6--views)
10. [The block system, which cuts across all of it](#10-the-block-system-which-cuts-across-all-of-it)
11. [Deliberately absent](#11-deliberately-absent)
12. [Conventions](#12-conventions)
13. [Open questions](#13-open-questions)

---

## 1. The ladder, and what moves a component up it

Two taxonomies already apply to this code and they are not the same thing.
Atomic design describes **composition depth**; Feature-Sliced Design describes
**direction of dependency**. Inventing a third vocabulary on top would be
exactly the sort of private dialect this repository is meant to avoid, so the
rungs below are atomic-design in spirit and FSD in address.

The useful part is the promotion rule, and it is not about size:

> **What moves a component up a rung is not how big it is. It is how much it
> knows.**

| Tier | Knows | Lives in |
| --- | --- | --- |
| **0. Primitive** | Design tokens. Nothing else. | `shared/ui/<name>/` |
| **1. Compound** | Other Tier-0 components. Still no domain noun. | `shared/ui/<name>/` |
| **2. Layout** | Where things sit on a screen. Not what they are. | `shared/ui/layout/` |
| **3. Entity** | Exactly **one** domain noun, and how to display it. Reads, never writes. | `entities/<noun>/ui/` |
| **4. Feature** | Exactly **one** verb. It **mutates** something. | `features/<verb-noun>/ui/` |
| **5. Widget** | Several entities and features, composed into a working area. | `widgets/<name>/` |
| **6. View** | A whole screen, and the route it answers to. | `views/<name>/` |

A 600-line virtualized table with keyboard navigation is Tier 1, because it
knows about rows and columns and nothing about applications. A forty-character
`ApplicationStatusBadge` is Tier 3, because it knows the ten statuses. Size is
not the axis.

**The rule is machine-checked, not aspirational.** The import linter already
forbids a lower layer from importing a higher one, `shared` from holding domain
types, and same-layer slices from seeing each other. If a component is on the
wrong rung, the build says so.

---

## 2. Constraints that shape every entry

Each of these is settled elsewhere and quietly determines a dozen entries
below. They are collected here so no one has to rediscover them.

**`shared` holds no domain types** (ARCHITECTURE §12.4). This is the constraint
people break first. `StatusBadge` in Tier 0 takes `tone="attention"`; it cannot
take `status="Screening"`, because the ten application statuses are a domain
fact and `shared` is not allowed to know one. The mapping from status to tone
lives in `entities/application`. The same split applies to every badge, dot,
icon and label that looks domain-shaped.

**No modal windows** (§12.9). Editing happens in place; creation happens in a
drawer. This removes an entire component from the inventory and promotes
`Drawer` and `InlineEdit` to load-bearing.

**Autosave at 400 ms, optimistic, rolling back on failure** (§12.9). Not a
behaviour bolted onto forms later — it is the default write path, which means
`InlineEdit` and the toast region are infrastructure rather than niceties.

**No literal text in JSX** (§13.1), enforced by `no-literal-text-in-jsx`. Tier 0
and 1 never contain copy; they accept it. Only components at Tier 3 and above
call `useStrings`, because only they belong to a namespace.

**Public pages are server-rendered** (§12.7). Every component a content block
can reach must render on the server. Interactivity is opt-in and marked, not
assumed — see the *Env* column in the tables.

**Cursor pagination, never page numbers** (§11.1). There is no `Pagination`
component with numbered pages, because the API cannot answer "how many pages".

**Behaviour comes from Base UI** (ADR-031). Unstyled, so the token invariant is
untouched: Base UI emits `data-state` attributes and no classes. Where a Tier-0
entry is a thin wrapper, the table says so — that entry is about *our* tokens
and *our* API, not about reimplementing focus management.

**A Tier 0 primitive may compose another Tier 0 primitive, if the one it
composes carries no domain knowledge and imposes no competing visual
decision of its own.** The ladder's "knows tokens, nothing else" is about
domain scope, not a ban on all reuse — reusing `Text` for a label is DRY, not
a tier violation, because `Text` itself still knows nothing but tokens.
`VisuallyHidden` is the clearest case: it is "the partner of every icon-only
control" by design, meant to be composed. The test is not "did this import
another component" but "did this import something that decides how the page
looks or means, on its own terms" — `Text`'s `variant`/`tone` resolution
counts as neither, since the composing component still chooses which variant
and tone to pass. A component that would need to hardcode a *specific*
`variant`/`tone` no matter what the caller wants — e.g. baking in a status
mapping — is where domain or presentation knowledge actually creeps in, and
that is the real line, not the import statement itself.

---

## 3. Tier 0 — Primitives

`shared/ui/<name>/`. Knows tokens. Never knows a domain noun, never fetches,
never mutates.

*Env* is `server` where the component renders without client JavaScript, and
`client` where it cannot.

### Typography and text

| Component | Purpose | Base | Env |
| --- | --- | --- | --- |
| `Text` | Renders one of the ten text styles; polymorphic element. The only way type is applied. | — | server |
| `Numeric` | Tabular figures and slashed zero, right-aligned by default. Every salary, count and date in a column. | — | server |
| `Truncate` | Line clamp with the full value reachable. | — | server |
| `VisuallyHidden` | Screen-reader-only text; the partner of every icon-only control. | — | server |
| `KeyboardKey` | Renders a key combination. Required by the action menu, which must show its shortcuts. | — | server |

### Marks and identity

| Component | Purpose | Base | Env |
| --- | --- | --- | --- |
| `Icon` | Wraps the chosen icon set behind one component so the set can be swapped. Decorative by default. | — | server |
| `Avatar` | One of only two round things in the system. | Base UI | server |
| `Dot` | The other round thing. Status dot; takes a `tone`. | — | server |
| `Logo` | Single instance, per §13.4 — never copied into a feature. | — | server |

### Actions

| Component | Purpose | Base | Env |
| --- | --- | --- | --- |
| `Button` | Tones: `primary` (monochrome, per ADR-029), `neutral`, `ghost`, `danger`. Sizes, loading state, leading/trailing icon slots. | Base UI | server |
| `IconButton` | Icon-only; an accessible label is a required prop, not an optional one. Tones `primary`/`neutral`/`ghost`/`danger` mirror `Button`'s; sizes `sm`/`md`/`lg` render as an equal-width/height square via the `control` height role. | — | server |
| `Link` | Inline navigation, and the `render` target when a button must be an anchor. | — | server |
| `SkipLink` | Visually hidden until focused, then jumps to the main landmark. One look, one job, no variants — required once by `AppShell`, WCAG 2.4.1. | — | server |
| `Toggle` / `ToggleGroup` | Two-state and exclusive-choice buttons: density, table-versus-board, theme. | Base UI | client |
| `Menu` | Dropdown with roving focus, typeahead, and a shortcut column. | Base UI | client |

### Inputs

| Component | Purpose | Base | Env |
| --- | --- | --- | --- |
| `Field` | Label, description, error, and the aria wiring between them, from `@base-ui/react/field`. Every control below is used inside one. | Base UI | server |
| `Fieldset` | Grouped controls with a legend, from `@base-ui/react/fieldset`; the legend labels the group via `aria-labelledby`, not a native `<legend>` — a deliberate upstream choice, not a gap. | Base UI | server |
| `Form` | Consolidated submission and server-error mapping. | Base UI | client |
| `Input` | Single-line text. | Base UI | server |
| `PasswordField` | Single-line text with a visibility toggle. Composes `Input` and `IconButton` rather than a third implementation of a text box. | Base UI | client |
| `TextArea` | Auto-growing multi-line. | — | client |
| `NumberField` | Numeric with increment, decrement and scrub. | Base UI | client |
| `MoneyField` | Cents in, cents out. **Never a float** — the API is integer cents throughout. | Base UI | client |
| `PercentField` | Displays a percentage, stores basis points. The 401(k) rate is `0–10000`, not `0–100`. | Base UI | client |
| `Select` | Choice from a known short list: work mode, seniority, filing status. | Base UI | client |
| `Combobox` | Choice from a long or remote list: company, contact, tag. | Base UI | client |
| `Autocomplete` | Free text with suggestions, where the value need not be in the list. | Base UI | client |
| `MultiSelect` | Several values as removable tags: tech tags, disqualifiers, allowed states. | Base UI | client |
| `Checkbox` / `CheckboxGroup` | | Base UI | client |
| `Radio` / `RadioGroup` | | Base UI | client |
| `Switch` | On/off settings, e.g. per-rule reminder enablement. | Base UI | client |
| `RatingScale` | A 1–5 scale, used by four unrelated fields: interest, fit, interview confidence, question difficulty. Generic because the scale is generic; the label is not its business. | — | client |
| `SearchField` | Text input with clear affordance and debounce. | Base UI | client |
| `FileDrop` | Drag-and-drop upload for résumés and media. | — | client |
| `Slider` | One value dragged or stepped from a numeric range. Single-thumb only — no dual-thumb range variant until a real call site needs one. | Base UI | client |
| `DateField` / `DateTimeField` | Interview times, posting dates, earliest start. **See open questions** — Base UI ships no date component. | ? | client |

### Surfaces and structure

| Component | Purpose | Base | Env |
| --- | --- | --- | --- |
| `Surface` | A themed background with a border, no shadow — the card in the page flow, per `composites/shadows.ts`. Three variants (`primary`/`elevated`/`inset`), `border-subtle` border, `card` radius. | — | server |
| `Panel` | Surface with header, body and optional footer slots. | — | server |
| `Separator` | Hairline from `border-subtle`, semantic or decorative. Behaviour is Base UI's `Separator`; `decorative` is our own translation to `role="none"`, since the installed Base UI version (1.7.0) dropped that prop. | Base UI | server |
| `ScrollArea` | Scroll container with a styled track/thumb (`surface-inset`/`border-strong`), so an inner scroll region never looks like a browser default. Both axes plus corner; one visual treatment, no variant. | Base UI | client |
| `Stack` / `Row` | Vertical and horizontal flow, gaps **only** from the spacing roles (`inline-tight` … `section-gap`). `Row` centres its cross axis by default (icon+label). These exist to make the scale unavoidable. | — | server |
| `Grid` | Column layout with token gaps; `columns` count is the one inline style, since no token names an arbitrary column count. | — | server |
| `AspectRatio` | Media boxes that do not shift on load. Hand-built on the CSS `aspect-ratio` property — Base UI 1.7.0 ships no `AspectRatio` primitive. | — | server |

### Overlays

All of these portal to `document.body`. That is not a preference: an ancestor
with `backdrop-filter`, `transform` or `filter` creates a containing block and a
`position: fixed` overlay collapses into it — a trap recorded in the repository
methodology rule.

| Component | Purpose | Base | Env |
| --- | --- | --- | --- |
| `Popover` | Anchored panel with collision handling. | Base UI | client |
| `Tooltip` | Hint for sighted users; never the only carrier of information. | Base UI | client |
| `Drawer` | **The creation surface.** With modals banned, everything that would have been "new item" dialog is a drawer. | Base UI | client |
| `ContextMenu` | Right-click actions on table rows. | Base UI | client |
| `PreviewCard` | Hover preview for a linked job or contact. | Base UI | client |
| `Toast` / `ToastRegion` | The failure channel for optimistic writes, and the host of undo. | Base UI | client |

### Disclosure

| Component | Purpose | Base | Env |
| --- | --- | --- | --- |
| `Collapsible` | **The third depth level.** Row expansion is this component inside a table row. | Base UI | client |
| `Accordion` | The FAQ block, and documentation sections. | Base UI | client |
| `Tabs` | | Base UI | client |

### Status and feedback

| Component | Purpose | Base | Env |
| --- | --- | --- | --- |
| `Badge` | `tone` plus `solid`/`subtle`. Backed by the existing `statusBadge` component tokens. | — | server |
| `Tag` | Removable chip for a tech tag or skill. | — | server |
| `Progress` | Determinate bar — the weekly application goal. | Base UI | server |
| `Meter` | A value within a range, semantically distinct from progress. | Base UI | server |
| `Skeleton` | Loading placeholder; must be still under reduced motion. | — | server |
| `Spinner` | Only for genuinely slow work — PDF render, media processing. Ordinary writes are optimistic and show nothing. | — | server |
| `Callout` | A toned block of explanation: tax warnings, the LLM budget notice, extraction-confidence caveats. | — | server |
| `LiveRegion` | `aria-live="polite"` by default, `"assertive"` where genuinely urgent; visually hidden, pairs with `VisuallyHidden`. For the status announcements `Toast` does not cover — a filtered result count, a background save finishing. | — | server |

---

## 4. Tier 1 — Compounds

`shared/ui/<name>/`. Composed from Tier 0, still with no domain knowledge.

| Component | Purpose |
| --- | --- |
| `DataTable` | Dense, virtualized, sticky-headed, density-aware, keyboard-navigable, with a slot for an expanded row. Column definitions come from the caller. Backed by TanStack Table and Virtual — headless, so no styling arrives with them. This is the single most load-bearing component in the product: the pipeline is a thousand rows at 16 ms a frame. |
| `Board` / `BoardColumn` | Generic column-and-card shell. The pipeline board is this with applications in it. |
| `Timeline` | Vertical event list with a connector. The `timelineConnector` component tokens already exist for it. |
| `KeyValueList` | Label-and-value pairs: parsed job fields, the compensation breakdown. |
| `Stat` | One large number with a label and optional delta. Analytics, and the Today header. |
| `EmptyState` | Icon, headline, explanation, one action. Every list needs one and they must not each invent it. |
| `ErrorState` | The same shape with a retry, for a failed query. |
| `LoadingRegion` | Skeleton composition matching the shape of what is loading, rather than a spinner in the middle of nothing. |
| `InlineEdit` | Click to edit, 400 ms debounce, optimistic write, rollback with a toast. **The default write interaction of the product**, not a special case. |
| `ActionMenu` | `Menu` plus a `KeyboardKey` column and a final row pointing at the command palette — the menu says out loud that it is a short list of the frequent, not the whole set (ADR-030). |
| `CommandPalette` | The other surface of that same action set: `Dialog` plus `Combobox` over a command registry. |
| `Toolbar` | Grouped controls above a list or editor. | 
| `LoadMore` | Cursor pagination. Deliberately not numbered pages. |
| `SearchableList` | Filter field over a list with an empty state — contacts, media, strings. |
| `Prose` | Rendered rich text at the 72-character measure. Blog posts, documentation, legal pages, job descriptions. |
| `TableOfContents` | Anchors for documentation pages. |
| `Breadcrumbs` | Documentation tree position. |
| `Money` | Formats integer cents. A component rather than a helper so that the tabular-figure rule cannot be forgotten. |
| `DateTime` | Absolute time in the user's configured zone. |
| `RelativeTime` | "21 days". Must render identically on server and client, then upgrade — reading the clock during render is the hydration bug from the methodology rule. |
| `CopyButton` | With a confirmation that does not steal focus. |
| `DiffView` | Two-column textual difference. Résumé version comparison, and revision history. |

---

## 5. Tier 2 — Layouts

`shared/ui/layout/`. These know where things sit, never what they are. Worth
naming as its own tier because three different screens turn out to be the same
shape, and discovering that after building all three is how a codebase gets a
`BriefLayout` that the résumé studio quietly imports.

| Layout | Used by | Notes |
| --- | --- | --- |
| `AppShell` | Console and admin | Sidebar plus main region, and the anchor for the floating action button. |
| `SidebarNav` | `AppShell` | 240 expanded, 64 collapsed — both already layout roles in the theme. |
| `TopBar` | `AppShell` | Search, theme, density, account. |
| `ThreeColumnLayout` | **Brief, admin page editor, résumé studio** | The same shape three times: a source column, a working column, a live preview. The brief adds the hard constraint — three columns must fit a 17–24″ display with no scrolling. |
| `TwoPaneLayout` | Contacts, media library | List beside detail. |
| `FocusLayout` | Today in Focus mode | Deliberately sparse: one decision, the system's reasoning, two actions, and one quiet line for everything else. Its emptiness is the design, so it is a layout and not a narrow `AppShell`. |
| `ContentLayout` | Public pages | Measure-constrained prose column. |
| `DocsLayout` | Documentation | Tree navigation, content, table of contents. |
| `PrintLayout` | Brief | The printable variant, which is a different composition and not a stylesheet afterthought. |
| `CenteredLayout` | Sign-in, fatal error | |

### Depth and density, in components

The two axes from ADR-027 and ADR-028 land like this, and neither is a prop
threaded through the tree:

| Axis | Mechanism |
| --- | --- |
| **Focus** | `FocusLayout` plus the `focus-card` widget. A separate screen with an opinion — not `Full` with fields removed. |
| **Full** | `AppShell` plus `today-board`. |
| **Row expansion** | `Collapsible` inside a `DataTable` row. |
| **Density** | A `data-density` attribute on the shell. Components read spacing from CSS variables that the attribute reswitches. It changes air only — row height, padding, table type size — and can therefore never add or remove a field, which is the whole point of keeping it orthogonal. *Density tokens are not built yet.* |

---

## 6. Tier 3 — Entity components

`entities/<noun>/ui/`. Each knows exactly one domain noun. They display; they
never mutate. This is where the domain vocabulary that `shared` is forbidden to
hold actually lives.

| Slice | Components |
| --- | --- |
| `application` | `ApplicationStatusBadge` (maps the ten statuses onto four tones), `ApplicationStatusDot`, `ApplicationRow`, `ApplicationSummaryCard`, `ApplicationEventTimeline`, `StatusDerivationExplainer` (why this record is in this status — the status is computed from events, so it must be explainable) |
| `job` | `JobCard`, `JobTitleLine`, `RequirementList` (must versus nice), `TechTagList`, `SalaryRange`, `WorkModeBadge`, `SeniorityLabel`, `SourceBadge`, `ExtractionConfidenceNotice`, `SnapshotViewer`, `InterestRating`, `FitRating` |
| `company` | `CompanyMark` (logo or initials), `CompanyLabel`, `CompanySizeLabel`, `AgencyIndicator` |
| `contact` | `ContactCard`, `ContactMark`, `AffiliationList`, `AffiliationTimeline` |
| `communication` | `CommunicationItem`, `ChannelBadge`, `DirectionMark`, `OutcomeBadge`, `TemplatePreview`, `ChannelLimitCounter` (the LinkedIn note limit is 200 characters and is shared with the server) |
| `interview` | `InterviewRoundCard`, `InterviewKindBadge`, `OutcomeBadge`, `ParticipantList`, `QuestionList`, `ConfidenceRating` |
| `document` | `DocumentChip`, `DocumentPreview`, `AtsReportPanel` (verdict, missing fragments, reading-order violations) |
| `resume-bullet` | `BulletItem`, `BulletTagList`, `ImpactMetric` |
| `compensation` | `CompensationBreakdown` (every line item, to the cent), `NetIncomeSummary`, `EffectiveRateStat`, `TaxWarningList` |
| `content-page` | `PageStateBadge` (draft, published, draft-ahead-of-live, archived), `RevisionListItem`, `PageKindLabel` |
| `media-asset` | `MediaThumbnail`, `AltTextIndicator`, `UsageList` |
| `reminder` | `ReminderRuleRow`, `ReminderThresholdSummary` |

---

## 7. Tier 4 — Feature components

`features/<verb-noun>/ui/`. One scenario each, and each one **writes**. The
slice list is fixed by ARCHITECTURE §12.5; what follows is what each contributes
to the interface.

Most contribute the same three things — a trigger, a drawer or inline form, and
an optimistic mutation — which is precisely why `Drawer`, `InlineEdit` and the
toast region sit in the shared tiers rather than being written five times.

| Slice | Contributes |
| --- | --- |
| `capture-job` | Manual entry drawer; extraction review screen for a low-confidence parse |
| `apply-to-job` | Apply drawer: résumé and cover-letter selection, stated salary, answers |
| `log-event` | Event menu and confirmation, from a row or a card |
| `rate-job` | Inline interest, fit and disqualifier editing |
| `schedule-interview` | Interview drawer with participants and questions |
| `record-communication` | Message drawer with template selection and variable filling |
| `send-connection-request` | Connection note composer with the character limit |
| `compose-resume` | Bullet selection, ordering, per-version override text |
| `render-resume` | Render trigger, progress, ATS report presentation |
| `evaluate-compensation` | Input form and live recalculation |
| `filter-pipeline` | Filter bar with active-filter chips |
| `global-search` | Palette-hosted cross-entity search |
| `configure-reminders` | Per-rule switch and threshold editing |
| `edit-page-blocks` | Block add, remove, reorder, and field editing |
| `publish-page` | Publish, unpublish, preview-link issuing |
| `restore-revision` | Revision comparison and restore |
| `upload-media` | Upload with alt text as part of the flow, not after it |
| `edit-strings` | Override editor showing the default value alongside |

---

## 8. Tier 5 — Widgets

`widgets/<name>/`. Composed working areas. The first group is fixed by §12.5;
the second is implied by §12.9 but unnamed there, and is flagged as such.

| Widget | Notes |
| --- | --- |
| `pipeline-table` | `DataTable` with application columns and row expansion |
| `pipeline-board` | `Board` grouped by derived status |
| `today-actions` | The Full board: sections, rows, actions |
| `job-summary-card` | |
| `brief-layout` | The six brief sections, each degrading to a placeholder panel on its own |
| `contact-history` | Affiliations and correspondence interleaved |
| `resume-studio` | Bullet bank, composition, live preview |
| `funnel-chart` | Own SVG — see open questions |
| `activity-chart` | Own SVG |
| `block-editor` | Drag-ordered blocks, generated field form, live preview |
| `block-renderer` | Resolves `kind` through the registry; **must not import a concrete block** |
| `hero-block`, `feature-grid-block`, `faq-block`, `cta-block`, `screenshot-block`, `quote-block` | One slice per block type |
| `quick-actions` *(implied)* | The floating button and its two-group menu. Not round — the only round things are avatars and status dots. |
| `command-palette-host` *(implied)* | Binds the same command registry to the palette |
| `app-sidebar` *(implied)* | Navigation with counts |
| `focus-card` *(implied)* | The single decision, the system's argument, two actions |
| `rejection-breakdown` *(implied)* | Named in §12.9 among analytics, absent from the slice list |

---

## 9. Tier 6 — Views

`views/<name>/`. One per screen, fixed by §12.5. Route files re-export them and
contain no JSX.

**Console** — `today`, `pipeline`, `job-detail`, `brief`, `contacts`,
`contact-detail`, `resume-studio`, `analytics`, `settings`.

**Admin** — `admin-page-list`, `admin-page-editor`, `admin-media`,
`admin-strings`.

**Public** — `landing`, `blog-index`, `blog-post`, `doc-page`, `changelog`,
`legal-page`.

---

## 10. The block system, which cuts across all of it

A block type is one file holding four things: a Zod schema, a list of field
descriptors, defaults, and a React component (ADR-013). Three consumers read
that one definition — the public page renders the component on the server, the
admin builds a form from the descriptors and previews the same component, and
the build exports the schema as JSON Schema for the backend to validate against.

The consequence for this inventory is a piece of machinery that has no natural
tier and is easy to miss until the editor is half-written:

**A field-descriptor renderer.** The admin form is *generated*, so something has
to map each descriptor kind onto a Tier-0 control — text to `Input`, rich text
to a prose editor, media to a picker, enumeration to `Select`, list to a
sortable repeater. It belongs beside the block contract in `shared/blocks`, and
it is the second registry in the frontend after the block registry itself.

Note also what the block layer may **not** contain: raw HTML is not an available
field type, by design.

---

## 11. Deliberately absent

Recording what will not exist is worth as much as recording what will, because
each of these will otherwise be proposed roughly once a quarter.

| Not building | Why |
| --- | --- |
| `Modal` / `Dialog` for content | §12.9 bans modal windows outright. Editing is in place, creation is a drawer. `AlertDialog` stays unused unless an irreversible destructive action appears that undo cannot cover. |
| Numbered `Pagination` | The API is cursor-paginated and cannot report a page count. `LoadMore` instead. |
| A generic `Box` with style props | It is a hole in the token discipline shaped like convenience. `Stack`, `Row` and `Grid` cover the real cases and only accept scale values. |
| A second colour vocabulary on components | No `color` prop that takes a palette step. Components take a `tone`, which resolves to roles. |
| A `Table` that is not virtualized | Two table components would diverge, and the dense one is the one under performance pressure. |
| Component-level theme overrides | The theme is the theme. A component that can be recoloured at the call site defeats the audit. |

**On `className`.** Components accept it, for layout and position only. This is
safe here in a way it would not be elsewhere: `no-raw-color-value`,
`no-arbitrary-color-class`, `no-raw-dimension-value` and
`no-unnamed-z-index-class` already fail the build on the abuses that would
otherwise make passthrough dangerous. The linter, not the type signature, is
what holds this line.

---

## 12. Conventions

**One directory per component**, with `index.ts` as its only public surface —
the same public-API rule the slices follow.

**Tone, not colour.** Every component that varies by meaning takes
`tone: "neutral" | "info" | "attention" | "success" | "danger"`. No component
takes a colour.

**Composition via Base UI's `render` prop** rather than wrapper elements, so a
`Button` can *be* a link without nesting an anchor inside a button.

**Copy arrives as props below Tier 3.** A Tier-0 or Tier-1 component that needs
its own words — the close affordance of a drawer, the retry of an error state —
takes them as required props. It does not reach into a namespace it cannot
belong to.

**Every interactive component is reachable, operable and visible by keyboard.**
The `focus-ring` utility already exists; using it is not optional. Accessibility
here is verified by three independent suites in CI, in both themes, and a
component that fails them is not finished.

**Reduced motion is handled globally** by the adapter, which collapses
transitions and animations. A component must not reintroduce motion through
inline styles or JavaScript animation that the media query cannot reach.

---

## 13. Open questions

**Date entry.** Base UI ships no date component, and dates are unavoidable —
interview times, posting dates, earliest start. Three routes: the native
`<input type="date">`, which is accessible and free but visually
unassimilable; React Aria's date components as a second, narrow dependency; or
building one, which is a genuinely hard accessibility problem. Undecided.

**Icon set.** Still open from the token specification §14: Lucide as the neutral
standard, or Phosphor, whose weight variants would track the density axis.

**`Icon`'s own API — deferred, deliberately, for a dedicated discussion.**
Raised and set aside while proposing Batch 1's variant surface, because it
turned out to need more than one round: how it wraps whichever icon set §13
settles on, and how brand marks (deferred separately — see the plan's Batch 0
decision on `SourceBadge`) eventually pass through the same component without
forcing it to know about them. The proposal on the table when this was
deferred:

- `icon: React.ComponentType<{ className?: string }>` — accepts the icon
  component itself (Lucide-compatible), not pre-rendered JSX, so `Icon`
  controls size and colour rather than trusting whatever the caller rendered.
- `size: "sm" | "md" | "lg"` — needs a **new component token**, since no size
  scale fits today (`sm: {dimension.4}` 16px, `md: {dimension.5}` 20px,
  `lg: {dimension.6}` 24px, default `md`), following the `statusBadge` /
  `timelineConnector` / `control` precedent of a component owning values that
  would mislead as a shared scale.
- No `tone` — colour inherits via `currentColor` from whatever text context
  renders it, rather than `Icon` making its own colour decision, on the
  reasoning that no known call site needs one yet (YAGNI) and the components
  that do carry status colour (`Badge`, `Callout`, `Dot`) already have their
  own `tone`.
- `label?: string` — present sets `role="img"` + `aria-label`; absent sets
  `aria-hidden="true"`, matching "Decorative by default" above.

None of this is decided. Revisit before `Icon` is actually implemented.

**Charts.** Hand-built SVG, decided. Three shapes are needed — funnel, weekly
activity, rejection breakdown — and they must take their colours from status
roles and stay legible in both themes. No dependency.

**Rich-text editing.** The blog block and the résumé summary need more than a
textarea, and no decision has been made. Raw HTML is excluded by the block
design, so whatever is chosen must produce structured output.

**Density tokens.** The axis is specified (ADR-028) and no tokens exist for it
yet. Until they do, `data-density` has nothing to switch.

**Component tokens.** Only `statusBadge` and `timelineConnector` exist.
`pipelineRow` is specified but unbuilt, and `actionCard` was deferred
deliberately.
