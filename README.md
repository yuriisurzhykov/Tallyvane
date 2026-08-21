# Tallyvane

A personal system for running a job search: capture a posting in one click,
keep an immutable record of everything that happened, and be ready for an
interview in ten seconds rather than ten minutes.

The name comes from *tally* — to keep count and write down — and *vane*, the
weathervane that shows which way things are moving. Those are the two halves
of the product: a complete record, and a read on where it is heading.

## Status

Scaffolding. The architecture is designed and reviewed; no feature code exists
yet. Directory structure and configuration are in place so that the rails come
before the first module, not after it.

## Where the thinking lives

| Document | What it covers |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | The whole system: modular monolith, layers, data model, flows, validation, decisions |
| [docs/frontend/](docs/frontend/) | Design tokens, and the frontend layer specifications as they are written |
| [docs/backend/](docs/backend/) | Module specifications as they are written |
| [docs/adr/](docs/adr/) | One file per architectural decision, referenced by ID from the code |

Read `ARCHITECTURE.md` first. Every non-obvious choice in this repository is
recorded there with the alternative that was rejected and why.

## Layout

```
backend/         Kotlin modular monolith — see backend/README.md
frontend-web/    Next.js app: blog + console — see frontend-web/README.md
frontend-admin/  Next.js app: CMS admin, a separate deployable — see frontend-admin/README.md
packages/        Shared workspace packages: design-tokens, frontend-shared, content-kit
extension/       Chrome extension, the only way LinkedIn can be captured
ops/             Deployment, database migrations, backup jobs
data/            Tax tables by year, generated block schemas
templates/       Typst templates for résumé rendering
docs/            Specifications and decision records
```

`frontend-web` and `frontend-admin` used to be one Next.js application with
three route groups (ADR-011) until the admin surface needed a security
property a route group can't express — see
[docs/adr/ADR-032-subdomain-split-and-admin-isolation.md](docs/adr/ADR-032-subdomain-split-and-admin-isolation.md).

## Principles that are enforced, not suggested

Architecture here is checked by machines. Layer boundaries are compile errors,
not review comments; import direction, naming, and token usage fail CI. The
reasoning is in `ARCHITECTURE.md` section 15 — reviewers get tired and agree,
compilers do not.

## Checking it

`pnpm verify` runs everything CI runs, in the order CI runs it. The parts, if
one of them is what you need:

| Command | What fails it |
| --- | --- |
| `pnpm typecheck` | Types, across every package |
| `pnpm lint` | ESLint: layer matrix, import cycles, public-API sidesteps, raw colours and dimensions in markup, unnamed stacking layers |
| `pnpm arch` | Committed token artefacts against a fresh compile, Feature-Sliced rules, the file-level dependency graph |
| `pnpm test` | Unit tests |
| `pnpm build` | `next build`, across every app that defines one |

Types run first on purpose: everything after them reads types, and a type error
otherwise produces a wall of unrelated failures that costs more to read than to
prevent. `build` runs last: it is the slowest of the five, so a cheaper check
fails first and the expensive one never has to run against code that was
already known-bad.

Every one of these is a repo-wide fan-out (`pnpm --recursive --if-present run
<script>`) — nothing here ever needs a `--filter`. Scoping a check to a single
package (a Playwright suite, `build-storybook`, `graph`) always uses
`pnpm --filter "./<path>" run <script>`, never a bare package name and never
`cd`.

These are the commands a human runs directly. A Cursor agent working in this
repo runs the same checks through `node .cursor/cli/agent-check.mjs
<action> [--package <path>]` instead — a hook denies it calling `pnpm`
directly — and gets back a compact pass/fail digest rather than the full raw
tool output. See [.cursor/rules/frontend-command-harness.mdc](.cursor/rules/frontend-command-harness.mdc)
for the full matrix and why both the path-based scoping and the wrapper exist.

`pnpm --filter "./frontend-web" run graph` writes the dependency graph
in DOT form, which is the cheapest way to see architectural drift — a picture
gets looked at, a rule list does not.
