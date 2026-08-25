# ADR-054. Health checks are decorated, and a cause is a type

## Status

Accepted.

## Context

§16 needs a probe an orchestrator can trust. The first implementation put three
responsibilities in `HealthReporter.OverChecks`: ask every check, apply each
check's declared `timeout`, catch whatever a check threw, and fold the answers into
a `HealthReport`. Causes were free text — `Health.Down(reason: String)`.

Two defects, neither visible in a passing suite.

The timeout bounded nothing. `withTimeoutOrNull` around `check()` cancels
cooperatively, and a check that blocks its thread — a JDBC socket read, which is
the case that matters — never reaches a suspension point to observe it;
`coroutineScope` then waits for that child anyway, cancelled or not. Every test
passed because every double suspended with `delay`. A blocking double under a 30ms
bound measured the full two seconds it blocked for. Starting the check with `async`
and racing `await()` failed identically, the task still being a child of the report.

The free-text reason made §17 a matter of recall. Any producer could pass a
driver's message through, and those carry hosts, ports and credentials.

## Decision

A bare `HealthCheck` may be slow and may throw. Two decorators, applied by `app`,
make it neither:

- `HealthCheck.Bounded(delegate, within, abandoned)` starts the delegate in a
  `CoroutineScope` owned by `app` and awaits *that*. Awaiting a task outside the
  caller's own scope is a suspension point, so the answer arrives within `within`
  and unfinished work is left behind rather than waited for.
- `HealthCheck.Contained(delegate)` turns a failure into
  `Health.Down(Ailment.Threw(type))`, keeping the exception's type and nothing else.

`timeout` leaves `HealthCheck`: a check declaring a bound it cannot honour is the
contradiction that produced the first defect. `OverChecks` folds answers and does
nothing else.

`Health.Degraded` and `Health.Down` carry an `Ailment` rather than a `String`:
`Refused(says)` for a check's own verdict, `Overran(bound)`, `Threw(type)`, and
`Dependencies(names)` for the aggregate.

## Consequences

The scope belongs to `app`, so a reporter has no lifetime, no `close()` and no
"already closed" state to define — an intermediate design had all three, and they
existed only because aggregation had been holding a decorator's responsibility.

Composition is `app`'s responsibility and its one hazard: an unwrapped check that
throws fails a probe, because `OverChecks` deliberately does not repeat what
`Contained` does. Slice 13 registers the checks and should assert the wrapping
rather than trust it.

`Bounded` cannot free the thread abandoned work occupies, and cancelling the scope
does not free it either. `socketTimeout` and `connectTimeout` on pgjdbc, and
`connectionTimeout` and `validationTimeout` on the pool, are therefore mandatory
when the Postgres check lands — this bound is the second line behind them, not a
replacement.

§17 becomes structural: `Threw` has nowhere to put a message. §11 gains a JSON
shape with a discriminator per case, so a probe's answer no longer depends on how
someone worded a string.

Test doubles that only suspend can no longer prove a bound. `HealthCheckBoundedSpec`
keeps a double that blocks its thread, which is the one that broke both earlier
attempts.

## Alternatives considered

**Bound inside `OverChecks`, owning a scope and implementing `AutoCloseable`.**
Rejected after implementing it: aggregation acquired a lifetime, a `close()`, and a
decision about what `report()` answers afterwards — three concepts the task never
asked for. Measured behaviour of the unhandled case was a `CancellationException`
escaping to a live caller, which structured concurrency reads as the caller's own
cancellation.

**Leave the bound entirely to the driver.** Correct as far as it goes, and still
required, but it makes the port's promise conditional on someone remembering to
configure a pool — the silent-omission failure this repository keeps refusing.

**One `Safe` decorator instead of two.** Less wiring in `app`, but "do not exceed a
bound" and "do not propagate a failure" are separate reasons to change one file.

**Keep the reason a string and forbid messages by review.** That is the convention
the defect already survived.
