# ADR-048. Minting a UUID is ambient randomness, not ambient time

## Decision

`no-ambient-random` gained three markers when Kotlin 2.4 moved UUID generation
into the standard library: `Uuid.random`, `Uuid.generateV4` and `Uuid.generateV7`.
The last is written without parentheses on purpose, so it also matches
`generateV7NonMonotonicAt` — both v7 generators draw their suffix from the
platform CSPRNG, and both are therefore a source of non-determinism that domain
code may not reach for.

`no-ambient-time` gained nothing, and that is the decision worth recording,
because `Uuid.generateV7()` demonstrably reads the wall clock.

## Why the time marker was added and then withdrawn

It was added first, on the obvious reasoning that a function reading the clock
belongs among the clock markers. Writing `IdGenerator.Uuid7` disproved it within
the hour.

A file is spared `no-ambient-random` when it implements `IdGenerator`, and spared
`no-ambient-time` when it implements `Clock`. `IdGenerator.Uuid7` implements the
former and not the latter, so the time marker flagged the one file in the tree
that is *supposed* to mint a time-ordered identifier — and nothing else, ever,
because every other route to a UUID is already caught by the random marker. A
marker whose entire effect is to accuse the correct implementation is not a guard.

The protection is complete without it. No file in `domain` or `application` can
mint a UUID by any route without firing `no-ambient-random`, whichever generator
it picks.

## Alternatives that would have bought the same protection for more

**Widen the `no-ambient-time` exemption to implementations of `IdGenerator`.**
Works, and grants every future implementer of that port a standing licence to read
the clock, by port name, in a rule about something else.

**Have `Uuid7` take a `Clock` and call `generateV7NonMonotonicAt(clock.now())`.**
Removes the ambient read honestly and reads better. Rejected because the method
name does not lie: two identifiers minted in the same millisecond may sort in
either order, and §8.1 asks for monotonicity. The determinism it appears to buy is
also smaller than it looks, since the 74-bit suffix stays random either way.

**`@ArchitectureException` on the file.** Spends one of ten slots and needs an ADR
to justify a situation the rule itself created. Wrong in kind: this is the
intended shape, not an exception to it.

## What the parentheses are for elsewhere

The withdrawn time marker was written `Uuid.generateV7()` precisely because
`generateV7NonMonotonicAt(instant)` is handed the moment it must use and reads no
clock; a bare `Uuid.generateV7` would have accused a caller who did the right
thing. That distinction survives in `arch-tests` as the reason the random marker
is deliberately broad while a hypothetical time marker would have to be narrow,
and `ambientRandomMarkersIn` pins the breadth on a literal snippet.

## Consequences

A new ambient source in the standard library is a new marker plus a case in
`AmbientMarkerSpec`, because `ArchitectureRulesSpec` only asserts that a fixture
directory is dirty and cannot say which marker fired. A marker nobody has watched
fire is the same unverified claim as a rule nobody has watched fail.
