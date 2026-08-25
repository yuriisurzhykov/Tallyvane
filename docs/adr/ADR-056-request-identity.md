# ADR-056. A request's identity is a W3C trace, carried by one coroutine context element

## Status

Accepted.

## Context

§16.6 requires structured JSON logs carrying a correlation identifier for the request
through every layer and module. Three things that requirement does not settle had to
be settled to implement it.

**What the identifier is.** It is the only thing that ties records together, and once
a module becomes a separate service it is the only thing that ties records across
processes. An identifier of our own can be put in a header, but nothing else in the
world can read it: not a reverse proxy, not an APM agent, not a model provider's
request log. W3C Trace Context already defines this value, and OpenTelemetry
propagates identifiers with it.

**How it survives a suspension.** `org.slf4j.MDC` is thread-local, and a coroutine
resumes on whatever thread its dispatcher supplies, so an entry put there once is
gone after the first `delay`, database call or HTTP call — with no error, just an
identifier missing from the rest of the request's log.

**Where the Logback configuration lives.** No library may own `logback.xml`: two of
them on one classpath and which wins is undefined. But a configuration authored only
in `app` cannot be exercised by the module that owns the format.

## Decision

**Shape and vocabulary are W3C Trace Context.** `TraceId` is 32 lowercase hex
characters, `SpanId` is 16, neither all zeros; both are validated at construction and
minted from `IdGenerator`'s UUIDv7 — the trace id from the whole value, the span id
from its random tail, since the leading bytes are a millisecond two requests can
share. MDC keys are `trace_id` and `span_id`. No OpenTelemetry SDK, no collector, no
spans.

**One coroutine context element carries it.** `TraceContext` is a
`ThreadContextElement` holding a `Trace`: it writes the two MDC keys as the coroutine
takes a thread and puts back whatever was there as it leaves. It is also the typed
read path, `TraceContext.current()`, for code that needs the identifier rather than a
log line.

**The configuration is split by role.** `platform:observability` owns
`logback-tallyvane.xml` in its main resources as an `<included>` fragment defining the
JSON appender and the root logger. `app`'s `logback.xml` (slice 13) includes it and
does nothing else. The module's own `logback-test.xml` includes the same fragment the
same way.

`platform:observability` exposes `slf4j-api` as `api` and takes `logback-classic` only
as a test dependency: the facade is what dependants compile against, the binding is
the composition root's choice.

## Consequences

An incoming `traceparent` can be continued rather than replaced when slice 11 wires
the HTTP boundary, which is what a service does. Adding real tracing later adds spans
to an existing vocabulary instead of renaming identifiers, and log records already
join whatever a tracing backend would produce.

Logs carry two fields rather than one, and until spans exist `span_id` is only the
request's own identifier.

The header mapping — parsing and rendering `traceparent` — is deliberately not here.
It belongs to the HTTP boundary in slice 11; slice 5 needs the value and its carrier.

The `<include>` mechanism is exercised by the module that owns the format, so what the
tests assert on is the encoder configuration that ships. The cost is remembering the
`include` when `app` arrives; without it there is no logging configuration at all,
which is loud rather than silent.

Any module that wants to log declares an edge to `platform:observability` in
`modules.yaml`, the same as the edges slices 10 and 11 already add.

## Alternatives considered

**An opaque identifier of our own, in a custom header.** One field instead of two and
slightly less code. The value is untranslatable: if OpenTelemetry ever arrives, log
identifiers and trace identifiers are different values and joining them is manual
work.

**Full OpenTelemetry now.** SDK dependencies in modules, spans placed by hand, a
collector process and a storage backend to administer. There is nowhere to view a
trace, and §16.6 asks for logs with an identifier, not traces.

**`MDCContext` from `kotlinx-coroutines-slf4j`.** A maintained implementation of the
same mechanism, but it mirrors the whole MDC map as captured at construction — a
regular source of surprise — and gives no typed access, so a second element would sit
beside it: two concepts where one does.

**`logback.xml` in `platform:observability`.** Simplest today, broken in slice 13 when
`app` supplies its own.

**Configuration only in `app`, with a separate test configuration in slice 5.** The
cleanest layering, and the test would verify a file that never ships.
