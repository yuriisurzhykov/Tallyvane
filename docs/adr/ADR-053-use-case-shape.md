# ADR-053. A use case is one action, published as an interface, implemented inside it

## Decision

A use case is **one action a user can perform**: sign in, sign out, upload a
document, delete a contact. Not a step inside how that action happens —
"complete the OAuth exchange" names a mechanism, and naming a use case after a
mechanism drags infrastructure into the vocabulary of a scenario.

It carries the marker interface `UseCase` from `platform:kernel`, is published as
an **interface**, and holds its implementation as a nested class. Consumers depend
on the interface; the composition root is the only place that names the concrete
type. The interface declares exactly one method, named for the action. `invoke` is
forbidden.

```kotlin
public interface SignInUseCase : UseCase {
    public suspend fun signIn(request: SignInRequest): SignInOutcome

    public class SignIn(/* ports */) : SignInUseCase {
        override suspend fun signIn(request: SignInRequest): SignInOutcome = TODO()
    }
}
```

## Why an interface, and why the implementation nests

Dependency inversion, stated plainly: a route receives an abstraction and the
wiring chooses what satisfies it. ENGINEERING-PRINCIPLES.md's condition on
extracted interfaces — "justified by the replacement actually happening" — is met,
because a route's test substitutes a use case rather than assembling the real one
over fake ports.

Its other objection is harder and is answered rather than dodged: "There is no
`Foo` and `FooImpl` […] a name answers *by what means*, and if only one means
exists the name says which one it is." A use case has one means, so no name can
answer that question. The resolution is that the pair is not
interface-plus-implementation-of-the-same-thing: the interface is named for the
action as a noun phrase (`SignInUseCase`), the nested class for the action itself
(`SignIn`), and neither is `Impl` wearing a different word.

Nesting is allowed here although an adapter may never nest on its port, and the
distinction is mechanical, not aesthetic. That ban exists because "a nested type
compiles into the module that owns the interface, which would drag a database
driver into a module whose whole purpose is to be driver-free". A use case's
interface and implementation both belong to the `application` layer of the same
module, so there is no boundary for anything to cross and nothing to drag. When a
second implementation appears, both move out and take names that say by what
means — the shape itself signals the change of situation.

Nesting also cost something that had to be repaired: `applicationUseCases()`
passed `includeNested = false`, so a nested implementation would have been
invisible to `single-public-method`, `usecase-has-test` and `web-one-usecase`. Four
rules would have gone quiet, which is the "comment that happens to compile" this
repository refuses.

## Why `invoke` is refused

`operator fun invoke` makes a call read `signIn(request)`, where `signIn` is a
field name the consumer chose. Two unrelated actions therefore read identically in
review, and renaming a field silently changes what the call appears to be. Naming
the method puts the word on the type, where nobody downstream can move it.

The cost is that the action's name appears twice — `signIn.signIn(request)`. That
is accepted, because it is the `parser.parse()`, `validator.validate()`,
`renderer.render()` shape: the type carries the subject and the method carries the
operation, and their agreement is a property rather than a redundancy. The
alternative — naming the type for a subject area, as `SessionUseCase` — stops the
type saying that it holds one scenario rather than five.

## `usecase-is-imperative` is deleted, and the marker is why

The rule required a use-case name to begin with one of twenty-eight words:
`Capture`, `Save`, `Submit`, `Advance`, `Schedule`, `Send`, `Render`, `Register`,
`Update`, `Archive`, `Publish`, `Record`, `Evaluate`, `Compose`, `Notify`,
`Ingest`, `Extract`, `Normalize`, `Score`, `Match`, `Recommend`, `Accept`,
`Dismiss`, `Complete`, `Start`, `Conclude`, `Observe`, `Log`.

Measured against the definition at the top of this record, that list rejects every
example of it: `SignIn`, `SignOut`, `UploadDocument`, `OpenJob` and `DeleteContact`
all fail, because `Sign`, `Upload`, `Open` and `Delete` are absent. It also accepts
`SaveThing`, which is not a verb phrase at all. A rule wrong in both directions
against the definition it exists to enforce is not worth extending word by word,
and each extension would have arrived as a red build on legitimate code.

What the list was really doing was *detection* — answering "is this a use case" for
three other rules. `UseCase` answers that exactly, so the vocabulary went with the
rule, along with its fixture and its row in §15.3. Naming is now a review matter,
which is honest: no mechanical check can decide whether a name is a verb without a
dictionary.

## Why the marker sits in `platform:kernel`

`application` may depend on `platform:kernel`, `platform:events`, its own domain
and contract, and other modules' contracts (`modules.yaml`). There is no shared
`domain` — every module has its own — so a type every module's `application` must
name has nowhere else to go.

Kernel's own admission test appeared to forbid it: "would every layer, `domain`
included, need to name it". `domain` names no use case, so `UseCase` failed. The
test was corrected instead, because by then it also excluded `TransactionRunner`
and `Verdict`, which ARCHITECTURE.md §6.13 puts in kernel by name. Three of seven
types failed a test that was therefore describing an intention rather than this
module. A dedicated platform module for one ten-line interface was the alternative:
six registrations — `settings.gradle.kts`, `modules.yaml`, §4.2, the layer
allowances, a build file and a README — to avoid amending one sentence.

## What `single-public-method` counts, and why it counts that

Every function on the interface, public or not, and the one that remains must have
no body.

A `private` member of a Kotlin interface is legal as long as it has a body — that
was compiled to check, not remembered — and its only purpose is to share code
between default implementations declared in the same interface, which is also why
Java 9 added the feature. A use-case interface declares one abstract method and has
no default implementations, so a private helper there can only be dead code or
logic that has crept into the abstraction. Counting public members only, which one
revision of the rule did, let it through silently.

The body check is the same argument arriving at the single declared method: a
default implementation would let the nested class omit the override, and a rule
that merely counted would have been satisfied.

## Three holes the fixtures did not cover

A single dirty fixture proves a rule fires once and says nothing about the ways
round it. Three were found by writing tests that looked for them, and each had
shipped as "done":

`usecase-is-interface` matched only a class implementing `UseCase` **directly**.
Implementations reach the marker through their use-case interface, so the shape
anybody would actually write — a class beside its interface — passed. The rule
caught the one form nobody writes.

`classes()` does not return objects, so `object SignOut : UseCase` was invisible.

`contract-no-logic`, rewritten in the same sitting to use the marker, had the same
blindness: interfaces and classes were checked, objects were not.

All three are closed, each with its own case in `UseCaseCornerSpec`, and the count
assertion that broke when a fixture file was added has been replaced with named
expectations — a bare number told us nothing about which shape had moved.

## Rejected alternatives

**A class per use case, no interface.** Fewer files and satisfies the "one means,
one name" principle without argument. Rejected because a consumer then depends on
the concrete type, and the composition root stops being the only place that knows
what was chosen.

**`interface CompleteSignInUseCase` with the verb in both names.** The first shape
tried. `completeSignIn.complete(request)` says the verb twice for no gain, and
"complete sign in" is a step in a mechanism rather than an action a user performs.

**`interface SignIn` without a suffix, class `CompleteSignIn`.** Reads best of the
three and needs no repetition. Rejected because the suffix is what makes a use case
identifiable at a glance in a constructor parameter list, which is where a route's
reviewer meets it.

**A generic method name — `execute`, `run`, `perform`.** Predictable, and says
exactly as little as `invoke` did.

**Extending the imperative word list.** Every extension is a build failure on
correct code first, and the list teaches people to edit the rule rather than think
about the name.
