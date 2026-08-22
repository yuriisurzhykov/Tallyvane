# Tallyvane — engineering principles

How a type is shaped, where knowledge of the outside world is allowed to live,
how failure travels, and what build logic may assume.

[ARCHITECTURE.md](../ARCHITECTURE.md) decides *where* code goes and
[README.md](README.md) explains how to move around the tree. This file decides
what the code looks like once it has a home. Where a principle here would
produce a shape that `./gradlew arch` rejects, the checker wins and the
principle is stated wrongly — say so rather than adding an exception.

Two parts. The first applies to every Kotlin file under `backend/`. The second
applies to `build-logic/` only, where the framework being served is Gradle
rather than the application.

## Part one — object design

### The file publishes one abstraction

A file's public surface is one type, and that type is an interface. Callers
depend on the interface; the implementations exist to be chosen, not to be
named by consumers. There is no `Foo` and `FooImpl`, because `Impl` says
nothing a reader could act on — a name answers *by what means*, and if only
one means exists the name says which one it is.

Implementations that do no I/O — decorators, caches, retries, in-memory
composites — nest on the abstraction, because they belong to its vocabulary
and have no independent identity:

```kotlin
interface Jobs {
    fun byId(id: JobId): Job?

    class Cached(private val origin: Jobs) : Jobs { /* ... */ }

    class Retrying(
        private val origin: Jobs,
        private val attempts: Int,
    ) : Jobs { /* ... */ }
}
```

An adapter that owns a technology is a top-level `internal` type in
`*:infrastructure`, named after the mechanism — `PostgresJobs`, not
`Jobs.Postgres`. The distinction is not aesthetic: a nested type compiles into
the module that owns the interface, which would drag a database driver into a
module whose whole purpose is to be driver-free.

The shape follows the state. No fields at all is an `object`, because a
stateless operation does not need one allocation per call. Only values is a
`data class`, which buys equality and a readable `toString` for free. A held
collaborator is an ordinary `class`.

### An operation is a type, not a string

Every distinct operation against the outside world gets its own type with its
own parameter list. A single function taking a command name and a bag of
arguments moves the checking from the compiler to runtime and to the reader.

```kotlin
interface LlmCall {
    fun answer(client: LlmClient): LlmAnswer

    data class Summarise(
        private val text: String,
        private val maxWords: Int,
    ) : LlmCall {
        override fun answer(client: LlmClient): LlmAnswer =
            client.complete(prompt = "...", limit = maxWords)
    }

    object Models : LlmCall {
        override fun answer(client: LlmClient): LlmAnswer = client.models()
        override fun toString(): String = "Models"
    }
}
```

`Summarise` cannot be constructed without its word limit and `Models` cannot be
given one. A generic call taking a name and a list of arguments is allowed in
exactly one place — inside the class that actually reaches the outside world,
where the shapeless form is the protocol rather than a convenience.

The payoff is that a new operation is a new type and nothing existing is
touched. If adding one requires editing a `when`, an enum, or a factory, the
abstraction is in the wrong place.

### Collaborators arrive as arguments

A collaborator needed only while the work happens is a parameter of the method,
not a field of the object. `LlmCall.answer(client)` above keeps every call an
immutable description of intent: it can be built, put in a list, compared,
printed and asserted on without a live client, an API key or a network.

What goes in the constructor is what the object cannot meaningfully exist
without. A retry decorator has no meaning without the origin it wraps, so the
origin is a constructor argument.

A use case is an object with one method, not a method on a service. Each one
names a plan and hands it to whatever executes it, rather than executing it
itself; that is what makes the plan assertable in a test that has no
infrastructure at all.

An abstract base class carries shared collaborators and nothing else. The
moment a branch or a step of a use case is pushed into a base class, the shared
thing was a collaborator and the branch was a missing object.

### One class per outside world

For each external technology exactly one class knows it exists. It is the only
file importing that driver, that client, that process API. Nothing above it
knows whether the store is PostgreSQL, whether the model is remote, or whether
the artefact is a file at all.

A port is named for what the consumer needs (`JobWriter`, `ResumeRenderer`),
not for the technology behind it, because the consumer's need is the part that
survives replacing the technology. A second class importing the same driver is
not extra convenience; it is the boundary leaking, and it turns a swap into a
search.

### Failure is a value inside and an exception at the edge

Inside the layers, a failure that the caller is expected to handle is a
returned value: a sealed outcome, a nullable, a `Result`. A sequence of steps
stops at the first failure and returns it, rather than throwing past the code
that knows what the failure means.

An exception is thrown at exactly one kind of place — the boundary with the
framework, where a failure stops being data and becomes an HTTP status, a
failed build or a dead-lettered message. That boundary is where the translation
lives, and it is the only place where the process learns the difference between
"this did not work" and "this must stop".

Two obligations come with declaring such a contract. A boundary class must
configure its dependency so the declared contract is actually true: a class
that promises to *return* a failure code must not sit on a library that throws
by default, or every caller above it ends up wrapping it in `try`. And where an
exception genuinely must be caught, the catch names the type it expects.
`catch (e: Exception)` also swallows the bugs, and a swallowed bug is
indistinguishable from a handled one.

### A message names the next action

A diagnostic tells the reader what to do next, not merely what was observed.
"Not found" and "invalid format" describe the program's state; they leave the
reader to guess the remedy.

```kotlin
require(templates.isNotEmpty()) {
    "No resume template in ${dir.absolutePath}. " +
        "Run ./gradlew :modules:resume:seedTemplates before rendering."
}
```

The same holds for a warning that precedes something surprising. If the next
step will restart a dependency, empty a cache, or make the application
temporarily unavailable, the message says so before it happens and says what
the state will be afterwards.

### Anything that can be logged can print itself

A type that can appear in a log owns a meaningful `toString()`. For a
`data class` the compiler provides it; for an `object` and for a class holding
collaborators it is written by hand. Nothing reaches a log as
`tallyvane.jobs.Jobs$Cached@1a2b3c`, and no caller assembles a description of
someone else's fields.

A composite value prints itself whole, as one multi-line block, so that
diagnosing a run is one interpolation rather than a paragraph of string
building at each call site.

The log level is chosen by audience, not by a feeling about importance.
Something a human is meant to read while waiting is `lifecycle` or `info` at
the top level; the step-by-step trace is `info`; a poll that repeats every
second is `debug`; a failure that does not stop the sequence is `warn`.

### A value is built from values

A value object's primary constructor takes values only. Knowledge of how to
extract those values from a framework — a result row, a request, a Gradle
model — lives in one named secondary constructor, so there is exactly one
place where the framework's shape becomes the domain's shape.

```kotlin
data class RenderRequest(
    val templateId: TemplateId,
    val locale: Locale,
    val target: Path,
) {
    constructor(row: ResultRow) : this(
        templateId = TemplateId(row[TEMPLATE_ID]),
        locale = Locale.forLanguageTag(row[LOCALE]),
        target = Path.of(row[TARGET]),
    )
}
```

Nothing becomes configurable while it is still derivable. A setting exists when
a real caller needs a different value, not because a future one might: an
option added early has to be honoured forever, and every option is a branch
somebody has to test.

### A name is verified, not assumed

A parameter is named for what it is, not for what the surrounding code
habitually calls things. A parameter of type `AppSettings` named `config`
misleads every reader after the first.

Where a type produces an identifier that someone outside will see — a task
name, a route, a queue, a column — the two are checked against each other. A
class whose name and its published identifier disagree is a rename waiting to
confuse a search.

### Waiting has a deadline

Any wait on external state has a bound: a deadline, a maximum number of
attempts, or both, and a failure message that says what was being waited for
and for how long. An unbounded poll turns a broken dependency into a hung
process, which is strictly worse than a failure — a failure is reported.

The wait itself is modelled with the same type as the actions around it, not as
a private helper, so that it is logged, named and composed on the same terms as
everything else in the sequence.

### Text for another interpreter is never smuggled through arguments

When a string will be parsed by something else — a shell, SQL, a template
engine, a URL — it is either built deliberately as that language, with
escaping, or it is not built at all and the structured API is used instead.
Slipping a fragment of one language into an argument list meant for another
happens to work whenever the receiver concatenates its arguments, and it stops
working the moment something does not.

If the goal is to discard output, suppress it on this side of the boundary
where the capability already exists, rather than appending a redirect and
hoping a remote interpreter honours it.

### The vocabulary has no unused words

Every member of a closed vocabulary has at least one caller. An operation that
was written for a scenario that never arrived is deleted, not kept: it is code
that compiles, is never exercised, and quietly claims to work.

### An abstraction for substitution arrives with the test that substitutes

An interface extracted so that a collaborator can be replaced is justified by
the replacement actually happening. When the port is added, so is at least one
test that runs against a hand-written `JobsFake` in `src/test` of the module
that owns the port.

Without that test the indirection has cost and no benefit, and it decays: the
next change starts leaking assumptions about the single real implementation back
into the callers, because nothing fails when it does.

## Part two — Gradle build logic

### Registration reacts; it does not demand an order

A plugin does not require that another plugin was applied first, and does not
check whether it was. It reacts to its arrival, so that the order of the
`plugins { }` block in a consumer's build script cannot be wrong:

```kotlin
project.plugins.withId("tallyvane.kotlin-module") { /* register tasks */ }
```

Anything generated per unit of the build — per module, per source set, per
variant — is generated by iterating what the build actually contains, never by
listing names it is expected to contain.

### A task is an adapter, not a place to put logic

A `@TaskAction` selects a use case, runs it and translates the outcome into a
build failure. Three or four lines. Everything else lives in objects that do not
import Gradle, which is what makes the logic testable with Kotest instead of
with a test build.

```kotlin
@TaskAction
fun validate() {
    val findings = GraphCheckRunner.Base(manifest(), projects()).runAll()
    if (findings.isNotEmpty()) {
        throw GradleException(findings.joinToString(separator = "\n"))
    }
}
```

The plugin class itself holds no logic either. It registers, wires and gets out
of the way; it is the composition root of the build, in the same sense that
`app` is the composition root of the application.

### Composition and order are separate declarations

`dependsOn` says what must also run. `mustRunAfter` says only in which order
things run if both were going to run anyway. Conflating them produces a graph
where asking for one step silently drags in another, and where removing a step
reorders the rest.

An aggregating task declares its members and has no `@TaskAction` at all. As
soon as an aggregator does work of its own, the graph stops describing the work
and starts hiding some of it.

### A variant of behaviour is another task, not a flag

Two related entry points are two tasks whose declarations differ by one line,
not one task with a command-line flag. `deploy` reuses what was built and
`deployAndBuild` additionally depends on the build; the difference is visible in
`./gradlew tasks` instead of in documentation nobody opens.

A task name reads as a verb, then the subject, then the qualifier —
`validateModuleGraph`, `installResumeTemplates`. The group is a single constant
in the plugin, and `description` is non-optional: an undescribed task is
invisible to the only discovery mechanism Gradle has.

### State that crosses the configuration boundary is plain values

What a task needs at execution time is declared as annotated properties holding
serialisable values, set during configuration. A task does not reach for
`Project`, an extension, or a live provider from inside its action.

The shape that makes this pleasant is a pair: one type captures the model at
configuration time, another reconstructs the same interface from the flat values
at execution time.

```kotlin
internal interface IncludedProjects {
    fun paths(): Set<String>

    // captured while configuring
    class Snapshot(root: Project) : IncludedProjects { /* ... */ }

    // rebuilt from flat values while executing
    class Wired(
        private val included: Set<String>,
    ) : IncludedProjects { /* ... */ }
}
```

Callers above the pair see one interface and never learn which side of the
boundary they are on. Without this, a task works exactly until the
configuration cache is enabled, and then fails in a way that points at
serialisation rather than at the design.

### `Ensure` means convergence

A task named `ensure*` brings the world to a state and is safe to run again. It
reports what it found — already correct, or corrected — because that
difference is the only interesting thing it knows, and it is what tells the
operator whether the surprising path was taken.

### A probe is an exit code that already exists

Before adding a "check whether X" operation, look at what the existing
operations already tell you. A command whose result distinguishes "already
present" from "created just now" is a state query, and using it as one removes
a whole redundant round trip along with the code that would have interpreted it.

The interpretation lives with the use case, named, so that a reader does not
have to know that a particular exit code carries meaning beyond success.

### Caching is refused honestly

When a task's result depends on state Gradle cannot observe — a device, a
running container, a remote deployment — inputs are still declared, and the
output is declared perpetually stale with a comment naming the invisible state:

```kotlin
inputs.files(artefact)
// The target's contents are outside Gradle's model: an unchanged artefact
// does not mean the target still has it.
outputs.upToDateWhen { false }
```

The alternative — inventing an output file so that up-to-date checking has
something to compare — buys a green build that is wrong, which is the one
outcome worse than a slow one.

### One fact, one API

A plugin reads a given fact through exactly one API. When a newer API
supersedes an older one, the older one is not kept alive for the single value
that was easier to obtain from it: the two models can disagree, and when they
do, the failure surfaces far from the mixture that caused it.

## The SOLID angle

These are not five labels applied afterwards; each one is what a specific rule
above buys.

**Single responsibility** is enforced by "one class per outside world" and "a
task is an adapter". A class that both decides and executes has two reasons to
change, and in build logic it also becomes untestable without a test build.

**Open/closed** is the point of "an operation is a type". Extension is adding a
type; nothing existing is edited, so nothing existing can regress. When that is
not true, the abstraction is wrong.

**Liskov** is what "the file publishes one abstraction" is protecting.
`Jobs.Cached` and `PostgresJobs` are interchangeable only as long as every
implementation satisfies the whole port and none of them narrows it — a
decorator that answers for some ids and throws for others is a subtype in name
only. Naming the port for the consumer's need is what keeps callers from being
written against a particular implementation to begin with.

**Interface segregation** is why a port is named for one need — `JobWriter`
rather than `JobService`. A consumer that only writes does not recompile when
reading changes.

**Dependency inversion** is why collaborators arrive as arguments and why the
composition root is a single named place. The direction of every dependency in
this tree is toward the abstraction, and `app` for the application and the
plugin class for the build are the only files allowed to know which concrete
type was chosen.
