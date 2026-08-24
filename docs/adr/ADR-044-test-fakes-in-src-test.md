# ADR-044. Test fakes live in `src/test`, not on the production type

## Decision

A test double is a handwritten class in the **test source set** of the module
that owns the port (`JobsFake` in `src/test/kotlin/…/JobsFake.kt`). It is not
a nested `Jobs.Fake` on the production interface, and it is not a top-level
`FakeJobFinder` in `src/main`.

Konsist rule `no-fake-in-main` flags any type whose name is `Fake`, starts
with `Fake`, or ends with `Fake`, including nested types, in production
sources. `Cached`, `Retrying` and `Abstract` may still nest on the port:
those are production implementations without I/O, not test doubles.

MockK and Mockito stay banned (ADR-043). The dummy still implements the port
in full so a new method fails the compiler and the conformance suite.

This applies to feature modules, `platform/*`, and binary Gradle plugins
under `build-logic/` (Konsist scans `platform/`, `app/` and `modules/`;
plugins are bound by the same cursor rule and by review).

Sharing a fake across modules, when that sharing exists, is Gradle test
fixtures — still not `src/main`. Until a second module needs `ClockFake`,
the fake stays in that module's `src/test`.

## Why

A nested class is compiled in the outer type's module and source set. `Jobs.Fake`
in `src/main` is `Jobs$Fake.class` in the production jar. Tests can see it
because every consumer already depends on that jar, which is convenient and
is exactly the leak: production bytecode carries a test double, and the port's
public surface includes a type that must not be wired in `app`.

`src/test` cannot add a nested class to a production type. The name is
therefore `JobsFake` in the same package, in the test source set.

## Rejected alternatives

**Nested `Jobs.Fake` on the port (ADR-043).** Smart-contract grouping looked
clean and kept the fake next to the methods it had to implement. It also
shipped in production. ADR-043's ban on MockK is unchanged; only the
placement of the handwritten fake is superseded.

**Top-level `FakeJobFinder` in `src/main`.** Still production. The old
`fake-is-nested` rule forbade this shape for the wrong reason (it wanted
nesting) and would have allowed `Jobs.Fake` in main.

**MockK "only for a dummy".** Unchanged from ADR-043: a `mockk()` double does
not implement the port.

**`java-test-fixtures` as the default home.** That source set is how a fake
is *shared* later. It is not a reason to put the first fake in main, and it
is not required until a second module's tests need the same type.
