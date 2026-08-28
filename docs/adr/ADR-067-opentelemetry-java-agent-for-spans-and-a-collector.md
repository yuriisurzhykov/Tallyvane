# ADR-067. The OpenTelemetry Java agent gives `app` real spans and a collector; `TraceContext` keeps its own MDC keys

## Status

Accepted. Supersedes the "no OpenTelemetry SDK, no collector, no spans" half of
[ADR-056](ADR-056-request-identity.md) — the rest of that record (W3C shape, `TraceContext` as a
coroutine-safe MDC element, the split-by-role Logback configuration) is untouched and still holds.

## Context

ADR-056 rejected a full OpenTelemetry SDK for one concrete reason: *"There is nowhere to view a
trace, and §16.6 asks for logs with an identifier, not traces."* Grafana Cloud (the CD/blue-green
plan's §6) is that somewhere now, which is exactly the condition `backend/.plans/backend-infra-cache-wiring.md`
already named as the trigger to revisit this by substance rather than by default.

The zero-code path — attaching `opentelemetry-javaagent.jar` via `-javaagent`, no SDK dependency in
any module — was chosen over hand-instrumenting spans, and checked live against the real `:app`
(`backend/playground/observability-otel/README.md`, 2026-08-28) rather than assumed from the
library's name:

- **Both HTTP and JDBC are auto-instrumented**, unprompted by any code change. Ktor's **CIO**
  engine (ADR-050) is covered by the agent's generic Ktor instrumentation, not only the more
  commonly-documented Netty engine — a SERVER span per request, a CLIENT span per SQL statement
  against HikariCP/pgjdbc, both carrying real W3C trace/span ids.
- **The agent's own Logback MDC injection collides with `TraceContext`'s.** Both write `trace_id`
  and `span_id` — confirmed as the agent's literal, unconfigured default key names against the
  project's own documentation before running anything. Once attached, the agent's injection wins
  outright: every log line for an instrumented request carried the agent's real span identity, and
  `TraceContext`'s own, `IdGenerator`-derived one never appeared in a log line again. What *did*
  still carry the old identity was `TraceHeader`'s outgoing `traceparent` response header, built
  from `TraceContext.current()` — so the header a caller receives and the log lines this process
  writes for the same request stopped agreeing, silently, the moment the agent was attached.

That is the fork this ADR had to settle, not a detail to bury in configuration: keep
`TraceContext` as the one identity and stop the agent from writing over it, make the agent's real
span the one identity everywhere (a real, if small, rewrite of `TraceHeader`/`TraceContext`'s
source of truth, plus a new `opentelemetry-api` compile dependency), or let both exist side by
side under different names.

## Decision

**The agent's Logback MDC keys are renamed; `TraceContext`, `Trace`, `TraceHeader` and
`IdGenerator` are not touched at all.**

`OTEL_INSTRUMENTATION_COMMON_LOGGING_TRACE_ID_KEY=otel_trace_id` and
`OTEL_INSTRUMENTATION_COMMON_LOGGING_SPAN_ID_KEY=otel_span_id`, set once, on the `app` container,
next to the other `OTEL_*` variables. This is the environment-variable form of
`otel.instrumentation.common.logging.trace-id-key`/`span-id-key` — confirmed against the actual
merged pull request that introduced them
([open-telemetry/opentelemetry-java-instrumentation#18851](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/18851)),
not a blog's paraphrase of it, and confirmed to predate the exact agent release this project pins.

Consequences of this specific choice, stated plainly rather than left to be discovered:

- `trace_id`/`span_id` in every JSON log line remain exactly what ADR-056 already produces —
  nothing about existing log-reading habits, dashboards, or the `TraceContextSpec`/`TraceSpec`
  test suites changes.
- `otel_trace_id`/`otel_span_id` appear alongside them, whenever the agent actually instrumented
  the code path that produced the log line (every HTTP request; a background job with no active
  span would carry neither). Cross-referencing a log line to its real span in Grafana Cloud is done
  through these two fields, not through `trace_id`/`span_id`.
- `TraceContext.current()` and the value in a `traceparent` header this process sends onward
  continue to be `TraceContext`'s own identity, unrelated to whichever span the agent recorded for
  the same call — a real seam between "this codebase's request identity" and "OpenTelemetry's
  span identity" that this ADR chooses to leave in place rather than closing, because closing it is
  a materially larger change (see Alternatives) for a benefit — one merged identity instead of two
  correlatable ones — this decision does not need yet.
- No new compile dependency anywhere in `backend/`. The agent is a JVM flag and an artifact in the
  Docker image, not a library any module imports.

**Collector: Grafana Cloud's own OTLP endpoint**, per the plan's already-fixed choice. `OTEL_EXPORTER_OTLP_ENDPOINT`
and `OTEL_EXPORTER_OTLP_HEADERS` (bearer token) are the two variables that point the agent there;
neither is a secret this repository commits, matching every other credential in `ops/.env.example`.

**Wiring lives entirely in `ops/`, not in `backend/`:** the agent jar is added to
`backend/Dockerfile`'s image (a downloaded artifact, verified by checksum at build time, the same
posture as any other binary dependency this image carries), and every `OTEL_*` variable is set
through `docker-compose.yml`'s `app` environment block, reading from `.env` the same way every
other secret and configuration value here already does.

## Consequences

A reader of a log line who wants the matching span opens Grafana Cloud with
`otel_trace_id`/`otel_span_id`, not `trace_id`/`span_id` — a naming difference worth remembering,
which is why it is written down here rather than left to be rediscovered.

Revisiting the seam between `TraceContext`'s identity and the agent's span identity — merging them
into one, per the "span is source of truth" alternative below — remains open, and is deliberately
not decided now: it has a real cost (a new compile dependency, a rewrite of tested code) for a
benefit this project does not yet need enough to spend that cost on.

`ADR-056` is not deleted or rewritten — the shape it chose (W3C, `TraceContext` as a
`ThreadContextElement`, the split Logback configuration) is exactly what let this decision be this
small. A record of "no SDK, no collector, no spans" staying accurate about *why* that was
reasonable in 2026-08 and a pointer to this one replacing that part of it, per this repository's
own convention for a superseded ADR, is worth more than editing history out of it.

## Alternatives considered

**Make the agent's active span the one identity everywhere** — rewrite `TraceHeader`/`TraceContext`
to read `Span.current()` (OpenTelemetry API) instead of `IdGenerator`, so the `traceparent` header
and every log line always agree, including with Grafana Cloud's own span ids. Rejected for now,
not permanently: it is the more architecturally coherent answer and remains available later, but it
needs a new compile dependency in `platform:observability` and a rewrite of code three specs
already pin (`TraceContextSpec`, `TraceSpec`, `TraceHeaderSpec`) for a benefit — one identity
instead of two correlatable ones — nothing today is asking for.

**Disable the agent's MDC injection entirely**
(`OTEL_INSTRUMENTATION_LOGBACK_MDC_ENABLED=false`). Simplest, and rejected because it throws away
the one thing worth adding an agent for: reading a log line back to the real span that produced it.
Renaming costs one line of configuration and keeps that reachable.

**Hand-instrument spans with the OpenTelemetry SDK directly**, no agent. Rejected on effort: the
agent's zero-code Ktor and JDBC instrumentation, confirmed live to already cover both without a
line of application code, is exactly the coverage hand-instrumentation would have to rebuild by
hand, for the same result.

**Wait for a schema-drift-style "prove it live" gate before trusting the key rename in
production.** Recorded as a real gap rather than smoothed over: the rename was confirmed against
the merged pull request that defines it, not re-run against a live process, because Docker Desktop
stopped responding partway through the investigation session. `backend/playground/observability-otel/README.md`
names the exact command to close that gap before this ships.
