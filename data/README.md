# data

Values that change on someone else's schedule, kept as data rather than code.

```
tax/<year>/    federal brackets, FICA parameters, per-state and per-locality rules
blocks/        JSON Schema per content-block type, generated from Zod at build time
```

## Tax tables

Rates, brackets and thresholds change every year. The compensation engine
therefore contains **no numeric literal relating to tax at all** — it takes a
`TaxYearTables` value as a parameter. Supporting 2027 is a new directory, not a
code change.

```
tax/2026/
├── federal.json          brackets by filing status, standard deductions
├── fica.json             rates, wage base, additional Medicare threshold
├── states/               one file per state
│   └── tx.json           e.g. { "kind": "none" }
└── localities/           e.g. New York City
```

Coverage starts with the states actually needed and grows by adding files.
Correctness is held by a table of worked examples checked to the cent, plus
property tests asserting that net never falls as gross rises and that
deductions plus net always equal gross.

## Block schemas

Generated, not written. A content-block type is defined once in TypeScript —
schema, field descriptors and React component in one file — and the build
exports its Zod schema here as JSON Schema so Ktor can validate block content
before writing it. One source, two consumers, no duplicated shape to keep in
sync.
