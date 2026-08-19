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
backend/     Kotlin modular monolith — see backend/README.md
frontend/    Next.js app with strict Feature-Sliced Design — see frontend/README.md
extension/   Chrome extension, the only way LinkedIn can be captured
ops/         Deployment, database migrations, backup jobs
data/        Tax tables by year, generated block schemas
templates/   Typst templates for résumé rendering
docs/        Specifications and decision records
```

## Principles that are enforced, not suggested

Architecture here is checked by machines. Layer boundaries are compile errors,
not review comments; import direction, naming, and token usage fail CI. The
reasoning is in `ARCHITECTURE.md` section 15 — reviewers get tired and agree,
compilers do not.
