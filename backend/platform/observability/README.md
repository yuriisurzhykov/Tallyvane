# platform:observability

What §16 asks the system to say about itself: whether it is healthy, and what
happened. Two concerns, both defined here as types and neither talking to a network —
a probe an orchestrator can trust and an alert a human can act on, and a log line that
names the request it belongs to. The route is §11's business and lives in `app`; so is
the choice of logging binding.

Nothing existing could be reused because nothing existing had an opinion about either.
`platform:kernel` holds ports every module needs — `Clock`, `IdGenerator`,
`TransactionRunner`, `UseCase` — and neither health nor log identity is one of those: a
module that stores applications has no reason to know what a probe is, and the kernel
has no reason to depend on slf4j. Keeping both here keeps the eventual metrics next to
them rather than scattered.

## Health

### What needed doing

Four separate questions, which is why there are four types rather than one.

`HealthCheck` is one dependency's answer about itself, with a stable name and a
readiness flag. `Health` is that answer: `Up`, `Degraded` or `Down`. Three states
and not two, because a dead LLM provider must be visible without taking the
application out of rotation — it stops extraction and nothing else. `HealthReport`
is what a probe gets: an aggregate, a `ready` flag and each check's own account.
`HealthReporter` produces one, and `OverChecks` is the only implementation.

### What was actually done, including the wrong turns

The first draft put everything in `OverChecks`: it asked the checks, applied each
check's timeout, caught what they threw, and folded the results. Two things were
wrong with it, and neither was visible in a green test suite.

**The timeout bounded nothing.** `HealthCheck` declared its own `timeout` and
`OverChecks` wrapped `withTimeoutOrNull` around `check()`. Every test passed —
because every test double suspended with `delay`, and `delay` observes
cancellation instantly. A real Postgres check does not suspend; it blocks a thread
on a socket read. Coroutine cancellation is cooperative, so a body that never
reaches a suspension point never observes the timeout, and `coroutineScope` waits
for every child, including a cancelled one that is stuck. Measured, not reasoned
about: a check blocking two seconds under a 30ms bound took the full two seconds.
A second attempt — start the check with `async` and race `await()` against the
timeout — failed the same way and for the same reason, because the task was still
a child of the report.

The bound therefore has to await work that is *not* the report's child, which is
what `HealthCheck.Bounded` does: it starts the delegate in a scope owned by `app`
and awaits that. `await()` is a suspension point, so the answer arrives on time
and the work is left behind. `HealthCheckBoundedSpec` keeps the blocking double
that broke the first two attempts, since a double that suspends cannot fail this
way and so cannot prove anything about it.

What no arrangement here can do is free the thread the abandoned work sits on;
cancelling the scope does not free it either. Only the driver can —
`socketTimeout` and `connectTimeout` on pgjdbc, `connectionTimeout` and
`validationTimeout` on the pool. Those are mandatory when the Postgres check
lands, not optional tuning, and this module's bound is the second line behind
them.

**A free-text reason made §17 everyone's job.** `Down(reason: String)` meant every
producer had to be trusted not to pass a driver's message through, and a driver's
message carries hosts, ports and sometimes credentials. `Ailment` replaces the
string with a case per cause, so `Threw` has nowhere to put a message and none can
arrive by accident. The aggregate names dependencies as a list instead of a joined
sentence a reader would have to parse back.

`OverChecks` ends up doing only what its name says. An intermediate design had it
own a coroutine scope, implement `AutoCloseable`, and answer a question nobody had
asked — what a report means once the reporter's lifetime has ended. All of that
was accidental: it existed because aggregation had been given a responsibility
that belongs to a decorator. Moving the bound out took the scope, the lifetime and
the third state with it.

## Logging, and the identity a log line carries

§16.6 asks for structured JSON logs carrying a correlation identifier through every
layer and module. Three things it leaves open had to be decided, all recorded in
[ADR-056](../../../docs/adr/ADR-056-request-identity.md).

**The identifier is a W3C trace, not one of ours.** `TraceId` and `SpanId` are that
standard's `trace-id` and `parent-id` — 32 and 16 lowercase hex characters, validated
at construction, minted from `IdGenerator`'s UUIDv7. Nothing new is depended on for
this: the standard's vocabulary is free, and it is what OpenTelemetry, reverse proxies
and APM agents already read. An opaque identifier of our own would be equally easy to
put in a header and impossible for anything else to interpret, which matters precisely
when a module becomes a separate service and the identifier is the only thing joining
two processes' records.

**MDC alone does not survive a coroutine.** `org.slf4j.MDC` is thread-local; a
coroutine resumes on whatever thread its dispatcher supplies. An entry put there once
is gone after the first suspension, with no error — just an identifier missing from
the rest of the request's log. `TraceContext` is a `ThreadContextElement` that writes
`trace_id` and `span_id` as the coroutine takes a thread and puts back whatever was
there as it leaves.

`TraceContextSpec` asserts the problem as well as the fix: one test shows a bare
`MDC.put` losing its value across `withContext(Dispatchers.IO)`. Without it, the
element's tests would pass equally well against an implementation that did nothing on
a single-threaded dispatcher, and nothing would say why the element exists.

`kotlinx-coroutines-slf4j` was the alternative, and would have worked. It mirrors the
whole MDC map as captured at construction, which surprises people regularly, and it
gives no typed read path — so a second element would have sat beside it to answer
"what is this request's identity" for code that needs the value rather than a log
line. One element answers both.

**The configuration is split by role.** No library may own `logback.xml`: put two on a
classpath and which one wins is undefined. But a configuration authored only in `app`
cannot be exercised by the module that owns the log format — and a test that builds
its own encoder passes while the shipping configuration is wrong. So this module owns
`logback-tallyvane.xml`, an `<included>` fragment holding the JSON appender and the
root logger, and `app` will include it from a `logback.xml` that does nothing else.
The module's `logback-test.xml` includes the same fragment the same way, so the tests
assert on the encoder that ships, and on the include mechanism too.

Which members the encoder emits was decided by looking at real output rather than at
the documentation's field list: `sequenceNumber`, `nanoseconds` and the logger
context's `name`/`birthdate` carry nothing in a single-process application and are
switched off; `formattedMessage` replaces the raw template plus its argument list.

`slf4j-api` is exposed as `api` because a dependant takes this module in order to log
and needs the facade on its own compile classpath. `logback-classic` is a test
dependency only — the binding is the composition root's choice, and a library that
picks one takes that choice away.

Parsing and rendering `traceparent` is deliberately absent. It belongs to the HTTP
boundary in slice 11; what is needed here is the value and its carrier.

## Why it is understandable, scalable, extensible

A new dependency is a `HealthCheck`, wrapped by `app` in `Contained` and `Bounded`
and added to the list. Nothing else changes. A check that becomes a remote call
when its dependency becomes a separate service changes only inside itself.
`OverChecks` asks every check at once, so a report costs the slowest check rather
than the sum, and adding the tenth check does not make the probe ten times slower.

The composition is `app`'s to make, which is also the one thing to be careful
about: an unwrapped check that throws will take a probe down, because containment
is `Contained`'s job and `OverChecks` deliberately does not repeat it. Slice 13
registers the checks and is where that should be asserted rather than remembered.

## Fault tolerance

`Contained` turns a failure into `Down`, so one dependency cannot fail a probe.
`Bounded` turns silence into `Down`, so one hung dependency cannot hold one.
Neither hides anything: both name what happened, in a type that cannot carry a
secret. No migration impact — this module owns no data.

## The SOLID angle

Single responsibility again in the logging half: `Trace` is the value, `TraceContext`
is what makes it follow a coroutine, and the encoder configuration is neither. Splitting
`TraceId` from `SpanId` is the same principle at the smallest scale — a trace id must
cross a process boundary unchanged and a span id must not, so they are not one type with
a length field. Both validate in `init`, so an invalid identifier cannot exist rather
than being checked wherever it is used. Dependency inversion is why the binding is not
here: this module names slf4j's facade and `app` chooses what implements it.

Single responsibility is the whole story of the health rewrite. Aggregating, bounding and
containing are three reasons to change, they were in one class, and the class grew
a lifetime it did not need as a result. Each is now its own type, and the two
decorators are separate from each other for the same reason: "do not exceed a
bound" and "do not propagate a failure" do not change together.

Open/closed: a new kind of failure is a new `Ailment` case, and a new kind of
protection is another decorator; neither edits `OverChecks`. Liskov is why the
decorators delegate `name` and `requiredForReadiness` with `by delegate` — a
wrapped check is usable anywhere a bare one is, and the spec asserts that rather
than assuming it. Interface segregation is why `timeout` left `HealthCheck`: a
check was declaring an obligation it could not meet, and callers had to trust it.
Dependency inversion is why `Bounded` takes a `CoroutineScope` instead of creating
one — the lifetime belongs to `app`, which knows when the application stops.
