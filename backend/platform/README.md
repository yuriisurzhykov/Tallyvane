# platform

Technical capabilities shared by every module. **No business logic lives
here, and nothing here may depend on `modules/*`** — that direction is
checked by a Konsist rule, not left to discipline.

The test for whether something belongs in platform: could it be lifted
into a different product without carrying a single idea about job hunting
with it? If not, it belongs in a capability module.

A platform module gets its own `README.md` when it contains code. Until
then the row below points at the sections of
[ARCHITECTURE.md](../../ARCHITECTURE.md) that already describe it, rather
than at a file that would have to invent the missing design. `kernel` is
the one module with code today:
[kernel/README.md](kernel/README.md).

| Module          | Owns                                                                 | Where described                                                                                          |
|-----------------|----------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `kernel`        | Shared vocabulary: ports for time and identity, reviewed exceptions, local fallback | [kernel/README.md](kernel/README.md)                                                                     |
| `events`        | `DomainEvent`, `EventPublisher`, `EventSubscriber`                   | [ARCHITECTURE.md](../../ARCHITECTURE.md) §4.5                                                            |
| `cache`         | `Counter` — the primitive a rate limit is built from                 | [cache/README.md](cache/README.md)                                                                       |
| `persistence`   | `TransactionRunner`, schema conventions, migration assembly          | [ARCHITECTURE.md](../../ARCHITECTURE.md) §4.6, §8.22                                                     |
| `http`          | Ktor plumbing: RFC 9457 errors, authentication extraction, `RouteModule` | [ARCHITECTURE.md](../../ARCHITECTURE.md) §11.1, §11.6                                                    |
| `outbox`        | Deferred side effects with exactly-once delivery                     | [ARCHITECTURE.md](../../ARCHITECTURE.md) §6.23, §8.9                                                     |
| `llm`           | `LlmProvider` and its cache, budget, metering and retry decorators   | [ARCHITECTURE.md](../../ARCHITECTURE.md) §6.3                                                            |
| `storage`       | `BlobStore`, content-addressed                                       | [ARCHITECTURE.md](../../ARCHITECTURE.md) §6.8                                                            |
| `exec`          | Running external binaries — Typst, cwebp — with timeouts and resource limits | [ARCHITECTURE.md](../../ARCHITECTURE.md) §6.13 — a port signature and operational limits; no API beyond that yet |
| `observability` | Structured logging and metrics ports                                 | [ARCHITECTURE.md](../../ARCHITECTURE.md) §16.6 — operational requirements; no interfaces yet             |
