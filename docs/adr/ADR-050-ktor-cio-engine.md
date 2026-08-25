# ADR-050. Ktor runs on the CIO engine

## Decision

The server is started with the CIO engine. ADR-002 chose Kotlin and Ktor; this
record only picks the engine underneath, which ADR-002 left open and nothing else
had settled.

The reason is the resource budget. `ops/README.md` allots the JVM 576 MB with a
384 MB heap out of 2 GB total, alongside PostgreSQL, two Node processes and
`cloudflared`. CIO is written in Kotlin coroutines and brings the least machinery
of the three engines; Netty brings its own buffer pools and native transport, which
is a larger resident footprint for throughput this deployment does not need.

That comparison is reasoned, not measured, and this record says so rather than
implying otherwise. §1.5 now requires the design to hold a hundred concurrent
requests under load test, so the engine will be measured there. The choice is
arranged to be cheap to reverse for exactly that reason.

## Why reversing it is cheap, and what is not cheap

The engine appears in one expression — `embeddedServer(CIO, …)` — and that
expression lives in the composition root. Modules never name it: by §11.1 each
module contributes a `RouteModule` and `app` mounts the list. Changing engine is
one line and one dependency.

The caveat is worth stating plainly, because "clean architecture will let us swap
it" is only true under a condition: the one-line swap holds while engine-specific
configuration stays unused. The moment a Netty- or CIO-only setting is relied on,
the swap becomes a real port, and the architecture will not have prevented it.

What the architecture does *not* protect against at all is replacing Ktor itself.
`RouteModule.install(route: Route)` is expressed in Ktor's own type, so Ktor is a
deliberate, accepted coupling across every `web` layer. That was decided knowingly:
the alternative was restating Ktor's routing in neutral terms, which buys a
portability nobody plans to use and costs a translation layer in every module.

## Rejected alternatives

**Netty.** The most battle-tested engine and the best documented, which is a real
argument. Rejected because its advantages are throughput and operational maturity
under load profiles this system does not have, while its cost lands directly on the
one budget §1.5 constrains.

**Jetty.** No advantage over either of the above here, and a servlet-container
lineage that fits a coroutine-first server least well of the three.

**Defer until measured.** Considered, and rejected because nothing can be built
without an engine and the measurement needs a running server to measure. The order
is: choose the cheap one, build, then measure under the §1.5 bar and revisit with
numbers.
