# ADR-062. The error contract is enforced by types, not by convention

## Status

Accepted. Built in `platform:http` with three arch rules behind it.

## Context

§11.6 fixed the *shape* of an error answer — RFC 9457, with an `errors` array — and left open
who decides that a given failure is a 422 named `validation-failed`. A first design answered
that and nothing else: a module maps its own failures, the platform renders. Reviewed, it turned
out to answer the easy half. The questions it could not answer were all of the same kind:

- What calls the mapping table? Nothing forced a route to use one.
- What makes a module have one at all?
- What stops a route answering `call.respond(BadRequest, "oops")` and bypassing everything?
- What stops `type` becoming a free string, so two modules describe one failure differently?
- What stops `basePath` being `"jobs"`, `"/Jobs/"` or `"/api/v1/jobs"` — three spellings, two of
  which mount somewhere nobody expects?

Each has the same failure mode: the code compiles, the endpoint answers, and the defect is
invisible until someone reads the JSON. A convention cannot close any of them.

Two constraints narrowed the answer before design started. `platform-knows-no-business` forbids
`platform:http` from importing a capability, so the platform cannot hold a table of module
failures. And `ENGINEERING-PRINCIPLES` says failure is a value inside the layers and an exception
only at the framework boundary — so a use case reports outcomes, and the route translates.

## Decision

**`Problem` has an `internal` constructor and a closed set of factories.** A module picks an HTTP
*meaning* — `invalid`, `forbidden`, `missing`, `conflicting`, `unavailable`, `unexpected` — and
supplies only what it alone knows: which field, which code, what to say. The platform owns the
protocol's vocabulary, the module owns its domain's, and the split satisfies
`platform-knows-no-business` without an exception: `forbidden` is HTTP, not jobs.

`unexpected()` takes no parameters at all. It is what an escaped exception becomes, and an
exception's message carries hosts, ports and occasionally credentials (§17). There is no argument
to leak them through.

**Failures are marked with `Failure` in the kernel and grouped under one sealed root.** The
marker lives in `platform:kernel` because `Problems<F : Failure>` needs something to bound its
type parameter with, and the kernel can hold it at no dependency cost.

**`Problems<in F : Failure>` maps one root, not one case.** Its `of` takes the whole branch, so
the `when` inside is exhaustive with no `else` — and a new failure breaks compilation until it is
mapped. An earlier sketch took `Throwable` and returned `null` for "not mine", which needs an
`else`; an `else` is exactly how a new failure silently becomes a 500.

**The type system, not a rule, forces the whole chain.** A sealed outcome makes the route's `when`
handle the failure branch; answering a failure requires a `Problem`; obtaining one for a module
failure requires a `Problems<F>`; holding one requires asking for it in the constructor; supplying
it requires `app` to build it. Break any link and the build stops.

**One place renders.** An interceptor on the send pipeline sets the status from the document, the
`application/problem+json` content type, the trace id in the header and in the body, and the log
line. A route responds with a value and arranges none of it, so none of it can be forgotten.

**`FailureTranslator` is a chain for what no use case reported** — a driver refusing, a pool
exhausted, a lock timeout, a bug. None of those pass through an outcome, so no `when` can see
them. Each link answers for what it recognises; `Unrecognised` ends every chain and produces the
detail-free 500.

**`RouteModule.basePath` is a `BasePath` value class**, validated on construction. §11.1 was
amended to match.

**Three arch rules cover what types cannot.** `failure-groups-under-root` (a failure is a sealed
root or nested in one), `failure-has-problems` (every root has a mapper), and
`web-answers-with-problem` (no route names a refusal status itself).

## Consequences

Adding an endpoint that can fail requires: a sealed outcome, a branch in the route's `when`, and
an entry in the module's mapper. Forgetting any of the three is a compile error, not a defect.

Adding a *kind* of failure that the platform's six meanings do not cover requires editing
`platform:http` — deliberately. A seventh HTTP meaning is a decision about the API's contract, and
making it visible in a diff of the platform is the point.

`detail` and `errors[].code` stay free strings, and that is stated rather than glossed: a module
can put a driver's message in `detail`, and only review catches it. Closing them would mean a
vocabulary of every module's validation rules living in the platform.

The renderer's placement was found by measurement, not reading. Intercepting at
`ApplicationSendPipeline.Transform` left three tests red: ContentNegotiation had already turned the
problem into plain `application/json` with a 200. `Before` renders first and hands on an
`OutgoingContent`, which ContentNegotiation leaves alone.

## Alternatives considered

**A central table in the platform mapping each module's failures.** Rejected twice over: it
requires the platform to depend on every module, and adding an error to one module would edit the
platform every other module depends on.

**Routes throw a module exception; a chain renders everything.** This was the author's own
proposal from a navigation engine, and it closed a real gap in the first draft — failures that
never reach the route. Adopted for exactly that, and rejected as the path for *foreseen* outcomes:
the endpoint's response surface then lives in the translator rather than the route, which slice
14's OpenAPI conformance test would have nothing to read; and the mapper's `else -> null` reopens
the silent 500 unless a type check precedes it, which is a line that can be forgotten.

**A `respondProblem` helper instead of a pipeline interceptor.** Rejected because
`no-top-level-functions` forbids the extension function it would have to be, and because a helper
can be not called, whereas an interceptor cannot be skipped.

**`@SerialName` on every property instead of a naming strategy.** Rejected: the first DTO that
omits one ships `salaryMinCents` into a contract promising `salary_min_cents`, and nothing fails.
The strategy is experimental API, and the opt-in sits on the one file that needs it.
