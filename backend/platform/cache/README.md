# platform:cache

`Counter` — how many times something happened to one key inside a moving window — and nothing
else yet. Decisions: [ADR-074](../../../docs/adr/ADR-074-cache-counter-only-in-memory.md).

## What problem it solves, and why nothing existing could be reused

`identity`'s sign-in and second-factor verification need to refuse an attempt after too many
failures inside a window — five wrong passwords in fifteen minutes, in the design this module was
built for. Nothing already in the tree answers "how many times has this happened recently": a
Postgres row could hold a count, but every increment would then cost a transaction and a network
round trip for a number that only has to be right for as long as one process runs, and
`platform:kernel`'s `TransactionRunner` has no notion of "or delete this row once its window has
passed" — that is a cache's job, not a database's.

## What needed deciding, and what was actually built

`backend/.plans/backend-infra-cache-wiring.md` worked through this direction on 2026-08-26 and
proposed two ports — a general value cache and a counter, because losing one costs latency and
losing the other changes behaviour — a first, in-memory implementation, and left the key-naming
convention and the failure behaviour under load open. Building this module turned "two ports" into
"one port, for now": nothing anywhere in the codebase reads or writes a cached value yet, and a
port built for a caller that does not exist is a shape guessed at from outside the code that would
use it. `Counter` alone has a real, immediate consumer — `identity`'s rate limiting, the very next
slice — so it is the one that shipped. The reasoning, including why the value cache is deferred
rather than dropped, is [ADR-074](../../../docs/adr/ADR-074-cache-counter-only-in-memory.md).

`Counter.InMemory` nests on the port rather than sitting beside it in a would-be `infrastructure`
layer — platform modules have none — for the same reason `Clock.Wall` and `IdGenerator.Uuid7`
nest on theirs (ADR-047): it reaches no technology beyond the standard library, only
`platform:kernel`'s `Clock`, so nesting drags no driver into a module whose whole purpose is to
stay free of one. `InMemory` had to be added to `arch-tests`' `NESTED_IMPL_ALLOW` for the same
reason `Wall`, `Uuid7` and `Process` did before it: `nested-impl-is-pure` silently skips every
nested class whose name it does not recognise, so an unlisted name would have been an unguarded
one, not a stricter one.

Testing a window closing needed a clock a test can move forward, and `platform:kernel`'s existing
`ClockFake` is deliberately a single pinned instant (`ClockFakeSpec` only ever asks for one "now").
Rather than duplicate a movable clock inside this module's own tests — a need every future
time-window rule in this codebase will hit again, refresh-token idle timeouts and
pending-authentication expiry among them — `MutableClockFake` was added to `platform:kernel`'s
`src/testFixtures` beside `ClockFake`, so the next module that needs one does not have to invent
its own.

## Why understandable, scalable, extensible

A reader looking for "how many times has this happened" finds one interface with one method, not a
generic cache API pressed into service for a job it was not shaped for. A second implementation —
Redis, when a measured reason arrives — is a new nested class or, once `Counter` has two
implementations, a `CounterConformance` suite the way `TransactionRunnerConformance` already
governs its own port; nothing that already calls `Counter` has to change, since it depends on the
interface. Extending this module to a real value cache later is a second port in the same package,
not a redesign of this one — the two were always going to be separate types for separate reasons
(ADR-074), so adding the second does not touch the first.

## Migration and fault tolerance

No schema, no migration — this module owns no table. Fault tolerance today is deliberately minimal:
`Counter.InMemory` holds its state in the same process as its only caller, so a crash or a restart
loses every window's count, which resets every account's rate limit to zero rather than to
"unknown" — the safe direction for a rate limiter to fail in. What this module does not yet provide
is durability across a restart or agreement across more than one running instance; ADR-074 names
exactly the two conditions that would make either matter, and neither holds while a single instance
of the application runs.

## The SOLID angle

**Single responsibility.** `Counter` answers one question — how many, inside a window — and
carries no threshold, no key convention enforcement, and no opinion about what a caller does with
the number it returns.

**Open/closed.** A second implementation is a new class satisfying the same interface; nothing that
depends on `Counter` is edited to add one.

**Liskov substitutability.** `Counter.InMemory` implements the whole of the port's one method with
the exact semantics its KDoc promises — a key not seen inside the window starts at 1 — so a future
implementation backed by a real store has to honour the same promise or it is not a `Counter`,
whatever else it might be called.

**Interface segregation.** One method. There was no second responsibility to split off in the first
place.

**Dependency inversion.** `Counter.InMemory` depends on `platform:kernel`'s `Clock` abstraction, not
on `System.currentTimeMillis` or `Instant.now()` — the same discipline `no-ambient-time` already
enforces everywhere else, which is what let this module's own tests move time forward deterministically
instead of sleeping a real thread to prove a window closes.

## 2026-09-02 — `count`, and the key-naming rule, on `identity`'s first real call

`count(key, window)` joined `increment` when `identity`'s password rate limiting became the first
real caller: it needs to check whether a threshold is already crossed *before* doing the work of
signing in, and only record a new occurrence on a failed attempt, not on every attempt. Without a
peek, the only way to ask "how many so far" was to record one — which would have counted successful
sign-ins against the same budget as failed ones.

The key-naming convention `backend/.plans/backend-infra-cache-wiring.md` called for — a key starts
with the name of the module that owns it, the same rule §4.1 already applies to a Postgres schema —
is now `cache-key-is-module-prefixed`, a Konsist rule of the same shape as `own-schema-only`: a
string literal passed to `increment`/`count` is read from the file's own source text and compared
against the module name its path names. It waited for this exact call site on purpose — a rule
guarding nothing but a synthetic fixture would have proven only that it compiles, not that it
protects anything.

## Not here

A general value cache and the failure behaviour of a future network-backed implementation are still
`identity`'s later slices or beyond — see ADR-074 for which of these is deferred and which is
dropped. `reset` — clearing a count outright rather than letting its window expire — has no caller
yet either.
