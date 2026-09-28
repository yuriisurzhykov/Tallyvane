# ADR-076. One structured logging contract, sinks selected by environment

## Status

Accepted.

## Context

The repository already had JSON stdout logs from the backend and a Java agent configured for
traces, but only the backend had an explicit logging path. Frontend request logs were absent, the
frontend OpenTelemetry SDK had no Logs processor, and the local Compose file disabled telemetry
without defining a shared application logging interface. As a result, Docker Desktop showed only
startup messages while Grafana had traces but no complete application log stream.

## Decision

Application code emits one language-neutral record shape: severity, stable event name, readable
body, and scalar attributes. Kotlin exposes `Logger`/`LogRecord`/`LogSink` from
`platform:observability`; TypeScript exposes the matching `Logger`/`LogRecord`/`LogSink` contract
from `frontend-shared`. The source interfaces are language-specific, while the event schema and
OpenTelemetry semantics are shared across the JVM and Node runtimes.

The environment chooses the sink through the standard `OTEL_LOGS_EXPORTER` and
`OTEL_SDK_DISABLED` configuration:

- Local Compose sets `OTEL_LOGS_EXPORTER=none` and disables the SDK. Both runtimes write one JSON
  record per line to stdout, captured by Docker's bounded `json-file` driver and visible in
  Docker Desktop.
- Production Compose sets `OTEL_LOGS_EXPORTER=otlp`. Next.js registers an OTLP Logs exporter through
  `@vercel/otel`; the JVM facade emits structured SLF4J/Logback records, which the existing
  OpenTelemetry Java agent exports to the configured Grafana Cloud endpoint.

Ktor and all three Next.js servers emit `http.server.request` records through those same facades.
They include method, path without query, status and duration; request bodies and headers are not
logged. Frontend access logging is installed as a Node preload so it covers Next.js standalone
servers without app-specific request handlers.

## Alternatives

- Keep the backend's SLF4J calls and add unrelated console logging to the frontend. Rejected because
  each runtime would continue inventing its own event shape and environment behavior.
- Write log files inside containers. Rejected because Docker's stdout capture and the existing
  OTLP pipeline already provide the required local and production destinations, without another
  rotation and collection path.
- Add Ktor `CallLogging` output independently of the application facade. Rejected because it would
  create a second backend access-log format rather than a common structured event.

## Consequences

Docker retains bounded local logs per container. Grafana Cloud receives logs for all four
application services using the same service resource names already used for traces. Adding another
language requires implementing the shared record contract and selecting its sink at that runtime's
composition root; existing event producers do not need to know whether output goes to stdout or
OTLP.
