# ADR-064. Generated code is for consumers of the API, never for the server

## Status

Accepted. Nothing is generated yet: the TypeScript choice is deferred with a stated trigger, and the
Kotlin answer is "no" rather than "later".

## Context

§11.7 says `docs/openapi.yaml` is the source of truth, that types are generated from it for the
frontend and for the extension, and that CI gates backward compatibility. In one sentence it names
three different jobs, and reading them as one leads to the assumption that "spec-first" means the
server is generated too:

| Job | Whose benefit | What does it |
|---|---|---|
| Types and clients | frontends, extension, a future mobile client | a generator |
| Conformance — does the server answer as described | ours | a test against a running server |
| Compatibility — does a change break existing clients | clients' | a diff of two spec versions in CI |

Only the first is generation. Conflating them produces the question this decision answers: if the
spec is the source of truth, why are the server's types written by hand?

## Decision

**No generated Kotlin for the server, and not as a deferral either.** The server is not a consumer of
the specification; it is the thing being described. Generating its DTOs would create a second source
of truth for its own types and require mapping between them for no gain, because what the server owes
the specification is *conformance*, and conformance is verified rather than generated.

It would also break guarantees this repository spent slices 11 and 12 building, concretely rather
than abstractly. `Problem` has an `internal` constructor — the whole mechanism of
[ADR-062](ADR-062-error-contract.md) rests on a module being unable to make one — and any generator
emits a `data class` with a public constructor, which the `problem-has-no-public-source` rule fails
the build over. `BasePath` is a value class that validates its own shape; a generator emits `String`.
Sealed use-case outcomes are not wire types at all, so generation cannot see the part of the design
that carries the most weight.

**Kotlin generation becomes a question again only if a Kotlin Multiplatform client appears**, which
§11.7 mentions as a possibility and no milestone plans. Building a generator for a client that does
not exist is the case YAGNI is for.

**TypeScript generation is deferred until the frontend actually calls the API**, and the trigger is
written down so the deferral has an end. Versions were resolved from the registry on 2026-08-26 so
the next round starts from facts rather than research:

- `openapi-typescript` 7.13.0 — types only, no runtime. The likely choice: it generates `.d.ts` and
  nothing else, so it cannot make decisions about the frontend's data layer, which §12 already made.
- `openapi-fetch` 0.17.0 — a typed `fetch` wrapper, a separate and later decision. Pre-1.0.
- `orval` 8.26.0 — generates react-query hooks, validation schemas and mocks. Rejected in advance for
  that reason: it decides the data layer.
- `@hey-api/openapi-ts` 0.99.0 — more flexible; the version says a major has not happened.
- `openapi-generator` — would bring a JVM into the frontend build to produce type declarations.

**Generated types are committed, with a `check` counterpart.** The pattern already exists in this
repository as `tokens:generate` / `tokens:check`: one command writes, the other regenerates and fails
if the result differs from what is committed. `api:generate` / `api:check` will mirror it.

Committing rather than generating at build time is deliberate: a change to the specification that
breaks a client then shows up as a diff in the types during review, instead of as a failing frontend
build later. This is the same preference for "cannot be forgotten" over "must be remembered" that
runs through the error contract and the driver bounds.

Output belongs in a workspace package — `packages/api-contract` — with separate entry points for the
console API and for capture, so that consumers depend on a package rather than on the YAML.

## Consequences

The server's types stay hand-written, and the specification is satisfied by a test rather than by
construction. Slice 14 owes that test, and its shape is already narrowed: OpenAPI 3.1 schemas *are*
JSON Schema (2020-12 dialect), so a JVM validator can check real responses from the Ktor test host,
and no second language enters the build. Which validator, and whether it truly supports 2020-12, is a
slice 14 decision to be measured rather than assumed.

The structural half of conformance already exists: `openapi-covers-routes` fails the build when a
served path is undocumented or a documented path is served by nothing. It cannot check methods,
statuses or bodies — that is the part waiting on a running server.

One correction to how §11.7 reads. "Types for the frontend and for the extension separately" is worth
keeping, but not for the reason it suggests: **type declarations do not ship**, they are erased at
compile time, so separate generation hides nothing from anyone who opens the extension. What it buys
is that the extension cannot accidentally grow a dependency on a console endpoint. Coupling, not
secrecy.

## Alternatives considered

**Generate Kotlin DTOs for the server and map to internal types.** Rejected above: it fails an
existing gate, and it adds mapping work whose only product is agreement with a file that a test can
check directly.

**Generate the server's route stubs from the specification.** Rejected for a second reason as well as
the first: §11.1's `RouteModule` is how a module publishes routes, and generated stubs would either
bypass that convention or be rewritten to fit it.

**Choose the TypeScript generator now.** Rejected because no frontend code calls the API yet, so the
choice would be made without the one input that matters — how the data layer wants to consume it. The
trigger and the candidates are recorded instead, which is the difference between deferring and
forgetting.

**Generate types at build time without committing them.** Rejected: drift becomes impossible, but so
does noticing a breaking change during review.
