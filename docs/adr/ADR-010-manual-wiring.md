# ADR-010. Dependencies are wired by hand, and the compiler checks the wiring

This record splits ADR-010 out of ARCHITECTURE.md §22, where it existed as one
sentence — "at this scale a container adds implicitness without removing work" —
with no alternative named and no cost admitted. The decision below keeps that
conclusion and supplies what was missing. It was written on 2026-08-26, when `app`
was about to be built and the question stopped being hypothetical.

## Decision

The composition root is written in Kotlin, by hand, and every dependency is a
constructor parameter or a property. No container resolves anything at runtime.

The structure is the one a container would give — containers, providers, modules,
lifetimes — expressed in types instead of a registry:

| What it is called in a framework | How it is written here |
| --- | --- |
| container | a class that knows how to build its own subtree |
| provider | a function, or `by lazy` for a deferred singleton |
| module | one `<Feature>Wiring` class per capability module |
| lifetime | `val` for one per application, function for one per call, `by lazy` for deferred |
| test override | pass a different argument |

The property this preserves is the one the whole backend is built around: a
dependency that was forgotten is a compile error, in the file that forgot it, with
the parameter's name in the message.

## The shape, and why it is not one file

Three of this repository's own rules decide the layout, so it is not a matter of
taste:

- `app-has-no-logic` admits only names ending in `Wiring`, `Configuration` or
  `Application` under `app/src/`.
- `one-top-level-class` admits one type per file.
- `no-companion-logic` flags a companion whose functions contain `if` or `when`.

Reading environment variables needs branching — "if absent, collect the name and
carry on so the failure lists every missing variable rather than the first" — so it
cannot live in a companion factory. That splits what looked like one class into two:
`EnvironmentConfiguration` reads and validates, `Configuration` holds values already
known to be good. The rule found a conflated responsibility before the author did,
which is the argument for having it.

## The entry point

`class Application` with `@JvmStatic fun main` in its companion, and
`mainClass = "tallyvane.app.Application"`. `@JvmStatic` is load-bearing: without it
the static method lands on `Application$Companion` and the JVM does not find `main`.

A top-level `fun main` is the more idiomatic Kotlin, and it was rejected for a
specific reason rather than a stylistic one. `no-top-level-functions` covers `app/`
— `Scopes.kt` scans `platform/`, `app/` and `modules/`, which is also why the spikes
under `playground/` have always been free to use one. Skipping the rule means
`@ArchitectureException`, and that mechanism is deliberately scarce: a named rule, a
reason of at least forty characters, an existing ADR file, and a project-wide budget
of **ten**, of which **zero** are currently spent. The companion form costs nothing
and is not exotic, so spending the first slot where an ordinary alternative exists
would set the wrong precedent for the remaining nine.

What is given up: Kotlin supports `suspend fun main` only for a top-level function.
Nothing needs it — `embeddedServer(…).start(wait = true)` blocks anyway, and the
suspending work at startup is wrapped in `runBlocking`, which is what that is for.

## Why reversing this is cheap in one direction only

Going from hand-written wiring to a framework is a contained edit: the wiring is
already gathered in one `Wiring` class, which becomes a graph declaration, and
annotations appear as the framework needs them. Going the other way means removing
annotations from every constructor in every module and taking a plugin out of the
toolchain.

That asymmetry, not a dislike of frameworks, is why the cheap direction is the one
left open.

## Rejected

**Koin, or any container that resolves at runtime.** It replaces "forgot a
dependency, so it did not compile" with "forgot a dependency, so a test you must
remember to write would have caught it". That trade is the opposite of what the
architecture tests, the module manifest and the layer rules were all built to buy.

**A service locator, however improved.** Considered seriously, because a locator with
containers, providers and modules sounds like the structure wanted here. It fails on
two counts. A missing registration is a runtime failure, exactly as with Koin —
having written the locator ourselves changes nothing about that. And whoever calls
the locator depends on it, so a constructor stops telling the truth about what its
class needs: the signature shows a locator and the real dependencies are visible only
inside the body. Test setup degrades from "pass these two objects" into "register the
right things first". `no-di-framework` would not have caught a hand-rolled one, since
it works by banned import prefixes — so this is refused on merit, and the gate is
recorded as blind to it.

Worth stating plainly: a locator does buy two things. It tolerates dependency cycles
by resolving late, and it can discover implementations by scanning. Neither is wanted
here — a cycle between modules is already refused by the module graph, and
`modules.yaml` makes wiring explicit on purpose.

## Deferred, not rejected: Metro and kotlin-inject

This is a distinction the author asked for explicitly, and it is not a formality.
Compile-time frameworks are **not** refused on principle: they keep the same "forgot
it, so it did not compile" property that hand-written wiring does, so on the
criterion that matters most they are equals, and the argument between them is about
volume of work, not safety.

They are postponed until three things are finished:

1. infrastructure is settled — services, the boundary with Cloudflare, the memory
   budget for the real machine;
2. authentication, authorization and access levels are complete;
3. the cache design is done.

Only then is the graph large enough for the benefit to be visible instead of
imagined. Today it is sixteen expressions, and that root has already been written
twice by hand in `playground/health` and `playground/http`, where it fits on a screen.
The measured facts to start that conversation from, gathered 2026-08-26:

**Metro** is at 1.0 and API-stable, a pure Kotlin compiler plugin using FIR and IR —
no KSP or KAPT source generation. Its API follows kotlin-inject's, it has Anvil-style
aggregation (`@ContributesTo`, `@ContributesBinding`), and it interoperates with
Dagger and kotlin-inject, so a partial migration is possible. Our Kotlin 2.4.10 is
supported and is in its tested-versions matrix.

**kotlin-inject** is KSP-based, slower on builds, and has no aggregation.

Two costs that will have to be accepted, not argued away, whenever this is revisited:

**Coupling to Kotlin releases.** The compiler plugin API is not stable, and Metro's
own documentation says forward compatibility is best-effort, roughly two minor
versions. In practice a Kotlin upgrade waits for a Metro release, and "waits" means
the build does not compile, not that something is inconvenient. KSP has the same
disease in a milder form.

**Annotations reach application classes.** `domain-no-annotations` covers only
`..domain..`, so the gate does not forbid `@Inject` in an application layer — this is
a principle rather than a rule, and it should be argued as one. `no-di-framework`
would have to be rewritten or dropped, which is a deliberate relaxation of a check
and needs its own approval.

One nuance in the framework's favour, recorded so the later discussion starts
accurately: Anvil-style aggregation does **not** defeat `modules.yaml`. An annotation
is only visible where a Gradle dependency exists, and `validateModuleGraph` checks
those edges against the manifest, so the module-level edge stays a visible line in a
diff. What becomes implicit is the binding table — which object satisfies which port
stops being written in one place. That is a readability cost, not a hole in a
boundary, and it was initially argued here as the latter, wrongly.

## When to revisit

When the three conditions above are met. The question to ask then is the one the
earlier plan already framed: does the root still read as a table of contents, and can
a module be added without reading all of it. If not, the candidate is Metro, with the
numbers from that day rather than the ones above.
