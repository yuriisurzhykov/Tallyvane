# platform:http

The edge: how a request finds a module, how JSON is spelled, what an error looks like, and what ties
a response to the log lines it produced. Nothing here knows a capability — `platform-knows-no-business`
forbids it, and the whole shape of the error contract follows from that one constraint.

Ktor 3.5.2, engine CIO (ADR-050). `RouteModule` names Ktor's own `Route`, deliberately: swapping the
engine costs one line in the composition root, swapping Ktor does not, and this signature is where
that is decided.

## What was needed, and why nothing existing would do

Before this module the backend had no way to be called. The four obligations it takes on are the ones
that cannot live in a capability, because each of them must be identical everywhere:

**Routing ownership.** §11.1 settles it: a module brings a `RouteModule`, `app` mounts it under
`/api/v1`. The alternative is one file listing every address in the system, which every module change
then edits.

**One JSON dialect.** `snake_case`, from `JsonNamingStrategy` rather than `@SerialName` per property —
the difference being whether it can be forgotten. The first DTO that omits an annotation ships
`salaryMinCents` into a contract promising `salary_min_cents`, and nothing fails.

**One error shape.** RFC 9457, per §11.6. Without it a client cannot distinguish "your input was
wrong" from "our database is down" — two failures with opposite handling — and every endpoint invents
its own envelope.

**One request identity.** The trace a response carries is the trace its log lines carry, so a user
quoting an id from a screen is enough to find the request (ADR-056).

## 2026-08-26 — the error contract, and why it is types rather than convention

The full decision is [ADR-062](../../../docs/adr/ADR-062-error-contract.md). What matters here is the
question that produced it, asked plainly during review: *what actually forces a route to use its
module's mapping table?*

The first answer was "the types do", and it was wrong. `Problem` had public factories, so
`call.respond(Problem.forbidden())` compiled, needed no table, and `failure-has-problems` only checked
that a table existed somewhere. Existence, not use.

What holds now, each link the compiler's:

```
sealed outcome            → the route's `when` must handle the failure branch
answering a failure       → requires a Refused, the only thing that renders as an error
Refused<F>                → does not compile without a Problems<F> of that exact branch
holding a Problems<F>     → the route asks for it in its constructor
supplying it              → `app` builds it, or `app` does not compile
```

And a module cannot sidestep the table, because it cannot make a `Problem` at all: the constructor is
`internal`, the seven factories live on `Answers`, and `Answers`' only implementation is `internal`
too. A module meets it as a *receiver*, inside `Problems.of` and `FailureTranslator.translate` — the
only two places in the backend where an error answer can come into existence.

Two smaller corrections came from the compiler rather than review. `Answers`' implementation cannot be
nested inside the interface, because Kotlin has no `internal` members in an interface: nesting would
have made it public and handed every module a constructor, undoing the whole thing. And
`FailureTranslator.translate` needed the same receiver, or a translator inside a module would have
been a public source of problems.

### Two mechanisms, for two different sources

`Problems` covers what a use case *reported*. `FailureTranslator` covers what nobody reported — a
driver refusing, a pool with no connections, a lock timeout, a bug. Those never pass through an
outcome, so no `when` in a route can see them.

Foreseen failures are answered as values rather than thrown, and the reason is not taste: slice 14
makes `docs/openapi.yaml` the source of truth with a conformance check, and that check needs the set
of an endpoint's responses to be readable from the endpoint. Thrown from arbitrary depth, it is not.

`Api` puts `TransportFailures` at the head of the chain and `Unrecognised` at the tail itself rather
than trusting `app`: a transport failure happens before any module's code runs, and a forgotten tail
would let an unrecognised failure reach Ktor's own handler.

## 2026-08-26 — what a live run found that eleven tests did not

The suite ran on Ktor's test host, which never opens a port. `playground/http` opens one, and three
things came out of it.

**A 500 carried no trace id — not in the body, not on its log line.** `"mdc": {}`, and no `trace_id`
field. An exception unwinds past the `withContext` that carries the trace, so by the time
`StatusPages` handles it the context element is gone. Exactly the case where a user quotes an id was
the case with no id. Fixed by also putting the trace in the call's *attributes*, which unwinding does
not touch, and restoring it around the error log.

**A malformed body answered 500.** Ktor throws `BadRequestException`, nothing recognised it,
`Unrecognised` did its job — and a client's typo was presented as our outage and logged at ERROR.
`TransportFailures` now maps it to a 400, and a seventh meaning, `malformed`, exists for it: 400 ("I
could not understand you") is a different statement from 422 ("I understood and refused").

**An unknown path answered with no body at all.** Ktor's own 404. The one error format held everywhere
except the most common failure an API has. A `status` handler gives it the shape.

All three are pinned in `ApiSpec`, which is now thirteen cases.

## 2026-08-26 — what an automated review found, and what it got wrong

Two comments arrived on the slice-11 diff. Both were checked by measurement rather than accepted or
dismissed, and they came out differently.

**The `traceparent` parser was reading four fields and validating two.** Correct, and now fixed. The
claim as written overstated part of it: a non-hex or all-zero *trace id* was already refused, because
`TraceId`'s constructor validates and `runCatching` sends a refusal to a fresh trace — the two cases
written for that passed first time. What was genuinely unchecked were `parent-id` and `trace-flags`,
so `00-<valid trace>-0000000000000000-01` and `…-zz` were both honoured. The standard is explicit
(§3.2.2.5): if `trace-id`, `parent-id` **or** `trace-flags` is invalid, a fresh header is created. So
this was not a judgement call — the KDoc already claimed both were refused, and the code simply never
looked at them. `TraceHeaderSpec` exists now, seven cases; three of them failed before the fix.

The review's framing — that this merges our requests into "an attacker- or proxy-selected trace" —
is not what the defect was. A well-formed header does exactly that *by design*: continuing a
caller's trace is the point of the standard, and `TraceHeader`'s KDoc already names the residual risk
and accepts it, because a trace id grants nothing. The real cost of the defect was a request filed
under a trace the standard says to discard.

**The 404 status handler replaces a module's own 404.** Correct, and worse than it sounds. The
handler is installed for every response with that status, not only for an unmatched path, so a module
that answers `missing("No payslip for August")` gets its detail thrown away:

```
expected: No payslip for August
actual:   {"type":"https://tallyvane.com/errors/not-found","title":"Not found","status":404,"trace_id":"…"}
```

The asymmetry that makes this specifically a 404 problem is worth naming: `Answers` is a closed set,
and 404 is one of the meanings a module can produce. A `status` handler is safe exactly when the
framework is the *only* possible source of that code.

The review's suggested fix — a fallback route instead of a status handler — was measured and does not
work:

```
method-agnostic fallback   POST /api/v1/probes/fine (GET-only route) -> 200 from the fallback
                           the 405 is gone, so "wrong method" now reads as "no such endpoint"
GET-only fallback          POST /api/v1/nowhere -> 404 with no body, uncovered again
```

So a fallback either eats the 405 or fails to cover anything but GET.

**A third defect fell out of measuring the second:** a wrong method answered `405` with an empty body,
which contradicted this module's own guarantee that the framework's failures follow the same contract.

## 2026-08-26 — the fix: stop keying on the status code at all

The first two candidate fixes both treated the status code as the trigger and then looked for a way to
tell the two sources of a 404 apart — a flag on the call, or a content-type sniff. The author rejected
a flag on the grounds that needing one is a sign the trigger is wrong, which turned out to be exactly
right.

What is actually different between the two events is visible one layer down. Measured in the send
pipeline, where this module already has an interceptor:

```
GET  /api/v1/nowhere  -> subject is HttpStatusCode   (the framework, answering by itself)
POST /api/v1/fine     -> subject is HttpStatusCode   (405, same)
GET  /api/v1/fine     -> subject is TextContent      (a route's body)
GET  /api/v1/empty    -> subject is HttpStatusCode   (a route's own `respond(NoContent)`)
```

A bare status arrives as the status; anything with content arrives as content. So the second branch of
`renderProblems` is keyed on that, and no status code is named anywhere. Consequences, all measured:
an unmatched path gets the shape, a 405 gets it without a line of its own, a 204 is untouched, and a
module's document is untouched because a document is not an `HttpStatusCode`.

One claim in the earlier note was too strong and is corrected here: a module *can* send a bare status,
`respond(HttpStatusCode.NotFound)` compiles and arrives as one. It gets wrapped, and nothing is lost by
that — a bare status carries no detail to lose, which is the whole difference from the old handler,
which threw away a document that had one.

**`Statuses`, and why it is not an eighth `Answers` meaning.** The 405 question answered itself once
the trigger moved. RFC 9457 §4.2.1 registers `about:blank` for a problem with "no additional semantics
beyond that of the HTTP status code", with the title being the status's own phrase, so a bare status
needs no `type` of ours and no enumeration of codes. That rendering is a port of its own —
`internal interface Statuses` with `AboutBlank` nested in it — for two reasons. It changes for a
different reason than "what a module's failure looks like" does. And `Answers` is public and handed to
modules, so an eighth factory there would give every module the power to name a status, which the
closed set exists to withhold.

Nesting the implementation is what `Answers` could not do: Kotlin has no `internal` members in an
interface, so nesting inside a public interface makes the nested class public. `Statuses` is `internal`
itself, so nesting is safe there — the same idiom as `HealthCheck.Bounded` and
`FailureTranslator.Chained`.

`ApiSpec` is twenty-three cases. Four of them are new, and each was watched failing first: the
detail-keeping case and the two `about:blank` assertions failed against the old handler, the 405 case
failed when 405 had no body, and the "below 400 is left alone" case was checked by widening the
threshold to zero and watching the 204 come back as a document.

## Where the rendering lives, and why it is a pipeline interceptor

`ApplicationSendPipeline.Before`. Not a helper function — `no-top-level-functions` forbids the
extension it would have to be, and more to the point a helper can be not called, whereas an
interceptor cannot be skipped.

`Before` and not `Transform`: at `Transform`, ContentNegotiation had already turned the body into plain
`application/json` with a 200. Measured, three tests red. Rendering first and handing on an
`OutgoingContent` leaves ContentNegotiation nothing to convert.

## Understandable, scalable, extensible

A reader following one endpoint sees its whole response surface in one `when` and its HTTP meanings in
one file per module. A reader asking "what can this system answer" reads `Answers` — seven cases, and
the list cannot grow without editing the platform, which is where such a decision belongs.

Adding an endpoint that can fail costs a sealed outcome, a branch, and a table entry, and forgetting
any of the three is a compile error. Adding a module costs one `RouteModule` and one line in the
composition root. Extracting a module into a service changes nothing here: the contract it implements
becomes an HTTP client, and the error shape it answers with is the same shape, because the shape lives
in a shared ADR rather than in each module.

## Fault tolerance

No stack trace, exception message or class name reaches a client (§17): the tail of the chain produces
a `Problem` with no field a message could occupy, and `Contained`-style narrowing is unnecessary
because the platform never passes the exception's text anywhere.

A dependency that fails does not take the answer with it — the request gets a status a client can act
on. A 4xx is not logged at ERROR, so the signal that means "a human must look" keeps meaning it.

## SOLID

**Single responsibility.** `Problem` is a document, `Answers` makes them, `Problems` maps one module's
failures, `FailureTranslator` maps what escaped, `Refused` pairs a failure with its table, `Api`
installs, `TraceHeader` reads and writes one header, `ApiJson` holds one configuration. Each has one
reason to change, and the split was not free — an earlier draft had `Problem` owning its own factories,
which is why the contract was breakable.

**Open/closed.** A new module extends the system by adding a `Problems` and a `RouteModule`; nothing
here changes. A new *HTTP meaning* deliberately does require editing `Answers`, because the closed set
is the guarantee.

**Liskov.** `Problems<in F>` is contravariant so a table for a branch serves every member of it, and
`FailureTranslator` links are interchangeable by construction: each answers for what it recognises and
returns `null` otherwise.

**Interface segregation.** `RouteModule` is two members; `Problems` is one; `FailureTranslator` is one.
Nothing implements a method it has no use for.

**Dependency inversion.** The platform defines `Problems` and `FailureTranslator` and knows no
implementation of either. Modules depend on those interfaces; `app` supplies the instances. The one
place the direction is inverted on purpose is `RouteModule`, which names Ktor's `Route` — recorded in
ADR-050 as a deliberate cost rather than an oversight.

## Not here, and whose it is

The engine's real configuration, the port, graceful shutdown and the actual route list belong to `app`
(slice 13); CIO is a test-only dependency here. Authentication is §11.2 and arrives with `identity`.
`CallLogging` is undecided: the question is not convenience but whether Ktor's plugin carries values
into MDC across the dispatcher hops this codebase makes, and ADR-056 already measured that a bare MDC
put does not — so it is a measurement, not a preference, and it has not been made.
