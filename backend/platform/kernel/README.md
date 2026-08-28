# kernel

The vocabulary more than one layer or module has to share. `domain` depends
on this module and nothing else; this module depends on nothing but the
standard library. Whether a type belongs here is the same test as for the
rest of platform — could it move to a different product without carrying a
single idea about job hunting — plus one more: does more than one layer or
module have to name it, with no layer able to reach a better home.

**That second clause used to read "would every layer, `domain` included, need
to name it", and it was corrected rather than worked around.** By the time
`TransactionRunner`, `Verdict` and `UseCase` arrived, three of this module's
six types failed it: `domain` names none of them, having neither
transactions nor use cases. The test as written excluded contents that
ARCHITECTURE.md §6.13 puts here by name, so it described an intention rather
than this module. The replacement is what actually decides the question:
`application` may see only `platform:kernel`, `platform:events` and
contracts (`modules.yaml`), so a type every module's `application` must name
has nowhere else to live — and inventing a platform module per shared
interface would be six registrations for ten lines of code.

[ARCHITECTURE.md](../../../ARCHITECTURE.md) §4.4 also allows
`kotlinx-datetime`. That coordinate is not on the classpath yet.
`Clock.now()` returns `kotlin.time.Instant` from the standard library.

## Why nothing existing could be reused

`java.time.Clock` and `kotlin.time.Clock.System` already know the current
instant. Calling either from domain code makes every time-dependent rule
non-deterministic: the test cannot pin "now", and two runs of the same
command disagree. The port exists so a test can construct a `ClockFake`
with a known instant. Direct use of `Instant.now()`, `LocalDate.now()`,
`System.currentTimeMillis()` or `kotlin.time.Clock.System` outside a type
that implements this interface fails Konsist `no-ambient-time`.

`UUID.randomUUID()` is the same shape for identity. A domain rule that
mints an id cannot be asserted against if the id is different every run.
`IdGenerator` is the collaborator; `IdGeneratorFake` yields
`…-000000000001`, `…-000000000002`. `UUID.randomUUID()`,
`kotlin.random.Random` and `java.util.Random` outside an `IdGenerator`
implementation fail `no-ambient-random`.

The port returns `kotlin.uuid.Uuid`, and production draws **UUIDv7** —
ARCHITECTURE.md §8.1 wants a monotonic id so Postgres clusters the index
and rows sort by mint time. No library is needed for that: Kotlin 2.4
stabilised `Uuid` and added `Uuid.generateV7()` to the standard library,
which prefixes a millisecond timestamp and fills the rest from the
platform CSPRNG. Only the generator functions stayed experimental, so a
production implementation opts in; this port and the fake use `parse` and
therefore do not.

An id is not a secret, and the distinction is load-bearing rather than
pedantic. The standard library says of `generateV7` that it is "not
recommended for use for cryptographic purposes", because a v7 value
publishes its own mint time and spends at most 74 bits on randomness. A
session token, a device token or a calendar-feed token therefore cannot
come from `IdGenerator`; each needs a port whose whole contract is
unguessability.

`kotlin.runCatching` already offers "try this, otherwise that". It catches
every `Throwable`. A cancelled coroutine then continues with a fallback
value, and `OutOfMemoryError` is treated as a condition worth recovering
from. Nested `try`/`catch` keeps cancellation honest but hides a linear
intent inside an indented tree. `Fallback` is the chain shape with the
honest catch: `CancellationException` is rethrown, `Error` propagates,
`Exception` is a failed attempt. See
[ENGINEERING-PRINCIPLES.md](../../ENGINEERING-PRINCIPLES.md).

A silent `// exception` comment, or a detekt suppress, would let a layer
crossing through without an ADR. `@ArchitectureException` is the reviewed
form: a named rule, a forty-character reason, an existing file under
`docs/adr/`, and a project-wide budget of ten. The checker, not this
annotation, owns those rules — see
[arch-tests/README.md](../../arch-tests/README.md) and ARCHITECTURE.md
§5.2.

## What is here, and what is not

Seven types exist: `Clock`, `IdGenerator`, `TransactionRunner`, `Verdict`,
`UseCase`, `ArchitectureException`, `Fallback`. The first two carry their production
implementations nested on them — `Clock.Wall` and `IdGenerator.Uuid7` —
while `ClockFake`, `IdGeneratorFake`, `TransactionRunnerFake` and
`TransactionRunnerConformance` sit in `src/testFixtures`, so another
module's tests can pin time, name ids and substitute a transaction without
declaring their own. That source set rather than `src/test` because
`src/test` does not cross a project boundary, and rather than `src/main`
because these must never ship (ADR-044, ADR-046). Being consumable makes
them `public`; `internal` stops at the module edge.
`TransactionRunner`'s production implementation lives in
`platform:persistence`, because unlike the other two it does reach a
technology; only the port is here, so that a use case can mark a
transaction boundary without depending on a database.
`Money`, `UserId` and `Slug` are named in ARCHITECTURE.md §4.2
(value objects also in §2.5) and are not in this tree. They belong here
when they are written. This file does not describe them as if they were.

`Outcome` was in that §4.2 list and has been removed from it rather than
implemented here. A search of the specification finds the name in that one
line and nowhere else: every operation that can fail already returns its
own named outcome — `FetchOutcome`, `LlmOutcome<T>`, `ProcessOutcome`,
`IntakeOutcome`, `AppendOutcome`, `DeliveryOutcome`, `RenderOutcome`,
`PublishOutcome`. A general `Outcome` in this module would be the second
competing result type that ENGINEERING-PRINCIPLES.md rejects in as many
words: "Two competing result types in one codebase is worse than either of
them alone."

## What was actually done

`Clock` and `IdGenerator` are interfaces with one method each. Callers
depend on the port; the composition root will name the production type.
The test doubles live in `src/test` as `ClockFake` and `IdGeneratorFake`,
in the same package, `internal`, not nested on the production type. A
nested `Clock.Fake` in `src/main` compiles to `Clock$Fake.class` in the
published jar, so every consumer of the port would ship a test double.
That is
[ADR-044](../../../docs/adr/ADR-044-test-fakes-in-src-test.md). `Cached` /
`Retrying` still nest: they are production. Until a second module's tests
need `ClockFake`, the fake stays here rather than in `java-test-fixtures`.

`UseCase` is an empty interface, which is unusual enough to justify. It buys
two things a naming convention cannot. It replaces a list of twenty-eight
imperative prefixes that the rules used to recognise a use case by — a list
that rejected `SignIn`, `Upload`, `Open` and `Delete`, so it contradicted the
definition of a use case it was meant to enforce, and that accepted
`SaveThing`, so it was wrong in both directions. And it lets a rule say "this
type is a use case" exactly, which is what `single-public-method`,
`usecase-has-test` and `web-one-usecase` all need and previously guessed at.
The shape it marks — interface with the implementation nested inside, method
named for the action, no `invoke` — is documented on the type itself and in
[ADR-053](../../../docs/adr/ADR-053-use-case-shape.md).

`Verdict` is here for one reason: `TransactionRunner` is here, and the block
it takes has to name the type. It is a directive rather than a result, and
that distinction is what keeps it from being the second competing result
type the engineering principles reject — the same objection that removed a
general `Outcome` from §4.2. What makes the distinction a fact instead of a
claim is `no-verdict-in-signature`: no function or property may declare
`Verdict` as its type, so it exists only as the last expression of a
transactional block. `TransactionRunnerFake` simulates rollback rather than
merely recording it, because a fake that reported a rollback it had not
performed would let the conformance suite pass for no reason. The whole
decision, including the hazard nothing checks, is
[ADR-052](../../../docs/adr/ADR-052-transaction-verdict.md).

`Clock.Wall` and `IdGenerator.Uuid7` nest on their ports rather than
sitting beside them. The rule that keeps adapters out of a port's module
exists for one reason — "a nested type compiles into the module that owns
the interface, which would drag a database driver into a module whose whole
purpose is to be driver-free" — and that reason does not reach here: these
two reach no technology at all, only the platform's clock and CSPRNG, both
already on this module's stdlib-only classpath. `app` was the other
candidate and is closed to them: `app-has-no-logic` refuses any type there
whose name does not end in `Wiring`, `Configuration` or `Application`, and
naming a UUID generator `IdGeneratorWiring` to satisfy a checker is a lie
told to a checker.

Neither is named `System`. `Clock.System` would read at a call site exactly
like the `kotlin.time.Clock.System` this port exists to keep out of domain
code, and the Konsist marker is the fully qualified name, so nothing would
have caught the confusion. `Wall` names the mechanism — the system's wall
clock — and `Uuid7` names the format. Both had to be added to
`NESTED_IMPL_ALLOW` in `arch-tests`, and not as a formality:
`nested-impl-is-pure` silently skips every nested class whose name it does
not recognise, so an unlisted name is an unguarded one.

`ArchitectureException` is a source-retention annotation on class,
function, file and property. Konsist skips the annotated declaration only
when `reason` is at least forty characters, `adr` names a file that exists
under `docs/adr/`, and `rule` is a known rule code. Raising the cap of ten
is a visible edit to the architecture tests. The identifier ARCHITECTURE.md
§5.2 uses for the mechanism is ADR-021; that record has not been split out
of §22 yet, so this file points at §5.2 and
[arch-tests/README.md](../../arch-tests/README.md) rather than at a missing
path.
[ADR-043](../../../docs/adr/ADR-043-backend-static-analysis-stack.md) is
the record for the checker stack that reads the annotation.

[`Fallback`](src/main/kotlin/tallyvane/platform/kernel/Fallback.kt) is the
only type in the tree that implements a chain of attempts ending in a
value. The scope is local recovery that never lets the failure leave the
function. A failure the caller must handle is a named sealed outcome for
that operation, not this class and not `kotlin.Result`. Attempts are
`inline`, so a chain may wrap a suspending call. A successful `null` is a
value. Catching `Exception` is broad on purpose in `of` and nowhere else —
the alternative is every call site catching broadly on its own.

The chain keeps the last failure; `orRecover` reaches it, `orElse` discards
it. Earlier it kept none — turning a failure into a named outcome needs the
cause.

No `orThrow`: rethrowing the last failure makes the exception type depend on
which attempt happened to be last, and a wrapper forces a `catch` on a type
the caller did not choose. Every terminal ends in a value, so `Fallback<T>`
stays a promise of a `T`.

## Wrong turns

`IdGenerator.next()` returned a `String` until §6.13 was read against it.
The specification says `fun next(): Uuid` and annotates it "UUIDv7,
монотонный по времени", and §8.1 requires UUIDv7 of every identifier in
the database. The port had been written before any caller existed, so
nothing failed while the two disagreed — the decay that "an abstraction
for substitution arrives with the test that substitutes" predicts, in a
port that had a fake but no assertion about the shape of what it yields.
The signature now follows the specification, and `IdGeneratorFakeSpec`
asserts the version nibble and the sort order, so a later change cannot
quietly return to a v4-shaped id.

A first rule, `fake-is-nested`, required the double to sit on the port.
That grouped the fake with the methods it had to implement and shipped it
in production. ADR-043 recorded that nested shape; ADR-044 reversed the
placement and replaced the rule with `no-fake-in-main`. The fakes in this
module were written under the later rule.

`or` and `invoke` are `inline` so the chain reads as a chain from
suspending and ordinary code. An inline function in another module cannot
call an `internal` declaration, so the constructor and `of` are
`@PublishedApi internal`. `of` also carries three suppressions —
`TooGenericExceptionCaught`, `RethrowCaughtException`,
`SwallowedException` — because this file is the one place the engineering
principles concede a broad catch. The suppressions stay here so they do
not appear at every call site.

## 2026-08-26 — two ports arrived with slice 13

**`Secret`, because a `data class` prints everything it holds.** `DatabaseAccess` was a `data class`
with `password: String`, so its generated `toString()` printed the password. Nothing had leaked, and
nothing prevented it: one interpolation of the whole object into a log line, or one exception message
that carried it, and a database password reaches disk. §17 forbids that, and a type enforces it where
a convention cannot.

It became a shared type rather than a `toString()` override on `DatabaseAccess` because a second
secret arrived in the same slice — the health service token — and "how a secret behaves" is one rule
with two users. Equality is constant-time, so the type is also usable for comparing a presented
credential; `ServiceToken` still carries its own comparison, and folding the two into one is worth
doing when that class is next opened.

The change reached about twenty construction and read sites — the fixture, `:migrate`, five spikes and
the persistence suites. Every read is now a visible `revealed()`, which is the point: the leak was
possible precisely because reading a password looked like reading a string.

**`Environment`, because `System.getenv` cannot be set from a test.** The JVM exposes the process
environment read-only. Slice 13 promised that a refusal names *every* missing variable, and there is
no way to test that against a machine's own environment — so the port exists to let a case state the
environment it is describing. `Environment.Process` nests on it, same as `Clock.Wall` and
`IdGenerator.Uuid7`, and `Process` was added to `NESTED_IMPL_ALLOW`, which widens what
`nested-impl-is-pure` inspects rather than relaxing it.

Unlike time and identifiers, this one has no gate: there is no `no-ambient-env` rule, so nothing stops
a file from calling `System.getenv` directly. Worth adding, not added here — a new gate is its own
decision.

## Understandable, scalable, extensible

A reader looking for "what time is it" finds `Clock`, not a static import.
A new shared value object is a new file in this package; nothing existing
is edited. A new ambient source of non-determinism is a new Konsist marker
in `SafetyRules.kt`, not a comment on this module. `Fallback` exposes one
cause, not a list: a list would need protecting from `CancellationException`
landing in it, and one cause is enough to name an outcome.

## Migration and fault-tolerance

`kernel` owns no tables and no schema. There is nothing to migrate. The
fault-tolerance this module actually provides is that a cancelled
coroutine does not resume with a fallback value, and that a domain rule
involving time or identity can be tested with the clock stopped.

## The SOLID angle

Single responsibility is why `Clock` does not also mint ids, why
`Fallback` does not also represent a caller-visible failure, and why the
annotation does not also run the checks — Konsist does. Open/closed is a
new value object as a new file, and a new ambient marker as a new string
in the existing rule, without editing callers of `Clock`. Liskov is why
`ClockFake` implements the whole port (one method, pinned) rather than a
partial stub, and why a decorator that answered for some instants and
threw for others would not be a `Clock`. Interface segregation is the
split into `Clock` and `IdGenerator` rather than a `KernelServices` with
both. Dependency inversion is why domain code takes `Clock` as a
constructor argument and never names the production implementation;
`app` is the file that will.
