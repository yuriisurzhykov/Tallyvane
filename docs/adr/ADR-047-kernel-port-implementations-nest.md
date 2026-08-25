# ADR-047. A kernel port's production implementation nests on the port

## Decision

`Clock` and `IdGenerator` carry their production implementations as nested
classes: `Clock.Wall` and `IdGenerator.Uuid7`. Both live in `platform:kernel`
beside the ports they implement, and `app` names them when it wires the
application.

This looks like a contradiction of the rule that an adapter is a top-level
`internal` type in `*:infrastructure`, so the reason that rule exists has to be
read exactly. ENGINEERING-PRINCIPLES gives it: "a nested type compiles into the
module that owns the interface, which would drag a database driver into a module
whose whole purpose is to be driver-free." The cost being avoided is a *dependency*
crossing into the port's module.

Neither of these reaches a technology. `Clock.Wall` calls
`kotlin.time.Clock.System`; `IdGenerator.Uuid7` calls `Uuid.generateV7()`. Both
are standard library, already on this module's classpath, which is stdlib-only by
design. There is no driver to drag, so the rule's reason does not apply and the
nesting is the shape the principles otherwise prefer: an implementation with no
I/O belongs to the abstraction's vocabulary.

## Why not `app`

`app` is where implementations are named, so it looks like the natural home. It is
closed to them by `app-has-no-logic`, which rejects any type in `app` whose name
does not end in `Wiring`, `Configuration` or `Application`. A UUID generator
called `IdGeneratorWiring` would satisfy the checker by lying to it, which is
worse than the problem it solves.

## Why not `System`

`Clock.System` would read at a call site exactly like `kotlin.time.Clock.System`,
the very thing this port exists to keep out of domain code, and no gate would
notice: the `no-ambient-time` marker is the fully qualified name. `Wall` names the
mechanism — the system's wall clock. `Uuid7` names the format rather than the
library, because the format is the contract and the library is not.

## Both names had to be registered

`nested-impl-is-pure` checks nested classes whose names it recognises and *skips
every other one*. Adding `Wall` and `Uuid7` to `NESTED_IMPL_ALLOW` is therefore
not bookkeeping: an unlisted name is an unguarded name, and the guard in question
is the one that would catch a framework import appearing in the port's file
later.

## Why UUIDv7, and why an id is not a secret

§8.1 requires UUIDv7 of every identifier, for the index clustering and natural
ordering the millisecond prefix gives. Kotlin 2.4 supplies `Uuid.generateV7()`,
which is monotonic and draws its suffix from the platform CSPRNG, so no library is
needed; only the generator functions remain experimental, so the implementation
opts in and the port does not.

The same standard library says of that function that it is "not recommended for
use for cryptographic purposes", because a v7 value publishes its own mint time
and spends at most 74 bits on randomness. A session token, a device token or a
calendar-feed token therefore cannot come from `IdGenerator`. Each needs a port
whose whole contract is unguessability, and this record does not create one.

## Rejected alternatives

**A new platform module for implementations that read ambient state.** A module
for two classes, and `platform/*` has no precedent for the shape.

**Leaving the ports without production implementations.** The state before this
record. It is what let `IdGenerator.next()` return `String` while §6.13 said
`Uuid` — an unexercised abstraction with nothing to fail when it drifted. That is
recorded in `platform/kernel/README.md` as a wrong turn rather than erased.
