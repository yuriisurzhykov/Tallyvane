# ADR-055. Without authorisation, health answers with a status and nothing else

## Status

Accepted.

## Context

§11.1 fixes snake_case for every response but says nothing about the shape of the
health response. It has to be settled before slice 12, or the routes will invent one
and `docs/openapi.yaml` in slice 14 will describe the invention.

There are two readers and they want different things. An orchestrator calls the
probes and looks at the status code; it has no use for a body at all. A human in an
incident opens the detailed `/api/v1/health` and wants to know which dependency, and
why.

Since [ADR-054](ADR-054-health-check-shape.md) a cause is not a string but a closed
`Ailment` type with four cases, so on the wire it needs a discriminated union rather
than free text.

## Decision

**Unauthorised callers get `status` only.** No `ready`, no `checks`, no causes.

```json
{ "status": "degraded" }
```

**An authorised `/api/v1/health` gets the breakdown.**

```json
{
  "status": "degraded",
  "ready": true,
  "checks": [
    { "name": "postgres", "status": "up", "took_ms": 3 },
    { "name": "llm", "status": "down", "took_ms": 2000,
      "cause": { "kind": "overran", "bound_ms": 2000 } }
  ]
}
```

The status vocabulary is `up`, `degraded`, `down`, lowercase.

`cause` is an object discriminated by `kind`: `refused` with `says`, `overran` with
`bound_ms`, `threw` with `type`, `dependencies` with `names`, `behind` with `versions`. By
the first rule `dependencies` and `behind` have no unauthorised rendering at all. A check
that is `up` carries no `cause`.

### Amended 2026-08-25 — `behind` added with the first real check

Slice 10 added `MigrationsApplied`, and no existing case fitted "the schema is behind the
code". `refused` with our own wording was the cheaper option and was rejected: the remedy
depends on the list, since one pending version means a deploy that started the application
before its migration step while several mean a deploy that skipped the step, and a sentence
makes a reader parse that back out. Schema versions describe what the system is made of, so
`behind` joins `dependencies` in having no public rendering.

`took_ms` is an integer number of milliseconds.

## Consequences

The public answer does not disclose what the system is built from. The names
`postgres` and `llm` tell a reader which dependencies exist and which one is
currently down, which is free reconnaissance for anyone looking for somewhere to
push. The probe stays useful to an orchestrator because readiness travels in the
status code, not the body.

The discriminator is `kind` rather than `type` for two independent reasons: the
`threw` case already has a field named `type` holding the exception's class name, so
`{"type": …, "type": …}` is impossible; and in RFC 9457, which §11 uses for errors,
`type` is a problem URI, so reusing the name for a different thing would need
explaining every time.

`ready` stays in the detailed answer even though it is formally derivable from
`status` and the per-check flags. The derivation is not obvious — a `down` optional
dependency does not make the application unready — so repeating that logic on the
reader's side is an invitation to get it wrong.

Slice 12 serves both shapes; slice 14 describes them in `docs/openapi.yaml`.

## Alternatives considered

**Serve everything without authorisation.** The system is personal, but the endpoint
is reachable from the internet through the tunnel, and the cost of withholding is
zero here: an orchestrator does not read the body.

**`cause` as a flat string.** Reintroduces exactly what ADR-054 removed: the reader
parses a sentence back into data, and the author of the string decides what goes in
it.

**`type` as the discriminator.** Collides with `threw.type` and with RFC 9457's
`type`.

**Duration as an ISO-8601 string, or as fractional seconds.** The first needs
parsing; the second gives `0.003` where an alert threshold reads `3`.

**A numeric status level.** Sorting three values is not worth making `2` in a log
require a lookup table.
