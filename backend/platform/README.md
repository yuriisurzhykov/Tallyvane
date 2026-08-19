# platform

Technical capabilities shared by every module. **No business logic lives here,
and nothing here may depend on `modules/*`** — that direction is checked by a
Konsist rule, not left to discipline.

The test for whether something belongs in platform: could it be lifted into a
different product without carrying a single idea about job hunting with it? If
not, it belongs in a capability module.

| Module | Owns |
| --- | --- |
| `kernel` | `Money`, `UserId`, `Slug`, `Clock`, `IdGenerator`, `Outcome` — the vocabulary every layer shares. Depends on nothing |
| `events` | `DomainEvent`, `EventPublisher`, `EventSubscriber` — the contract for reactions between modules |
| `persistence` | `TransactionRunner`, schema conventions, migration assembly |
| `http` | Ktor plumbing: error mapping to RFC 9457, authentication extraction, the `RouteModule` contract |
| `outbox` | Deferred side effects with exactly-once delivery |
| `llm` | `LlmProvider` and its cache, budget, metering and retry decorators |
| `storage` | `BlobStore`, content-addressed |
| `exec` | Running external binaries — Typst, cwebp — with timeouts and resource limits |
| `observability` | Structured logging and metrics ports |

`Clock` and `IdGenerator` are ports rather than static calls for one reason:
without them no domain rule involving time or identity can be tested
deterministically. Direct use of the current time or a random UUID outside the
implementations of these two ports fails the architecture tests.
