# platform:observability

Health, as a thing the rest of the system can be asked about. §16 wants a probe an
orchestrator can trust and an alert a human can act on; this module supplies the
types those two are built from, and nothing that talks to a network. The route is
§11's business and lives in `app`.

Nothing existing could be reused because nothing existing had an opinion about
health at all. `platform:kernel` holds ports every module needs — `Clock`,
`IdGenerator`, `TransactionRunner`, `UseCase` — and health is not one of those: a
module that stores applications has no reason to know what a probe is. Keeping it
here also keeps the eventual metrics and tracing next to it rather than scattered.

## What needed doing

Four separate questions, which is why there are four types rather than one.

`HealthCheck` is one dependency's answer about itself, with a stable name and a
readiness flag. `Health` is that answer: `Up`, `Degraded` or `Down`. Three states
and not two, because a dead LLM provider must be visible without taking the
application out of rotation — it stops extraction and nothing else. `HealthReport`
is what a probe gets: an aggregate, a `ready` flag and each check's own account.
`HealthReporter` produces one, and `OverChecks` is the only implementation.

## What was actually done, including the wrong turns

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

Single responsibility is the whole story of the rewrite. Aggregating, bounding and
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
