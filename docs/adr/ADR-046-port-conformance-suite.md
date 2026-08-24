# ADR-046. A port with two implementations has one suite both must pass, named `<Port>Conformance`

## Decision

When a port has more than one implementation, the tests that define its
behaviour are written once and run against every implementation. The suite is
named after the port with the suffix `Conformance`, and `port-has-conformance-suite`
fails a port that has two or more implementations and no such type.

In practice a port has exactly two implementations early on: the handwritten fake
in `src/test` (ADR-044) and the adapter that reaches the real technology. Those
are the two that must agree. If each has its own tests, the fake drifts — and the
drift is invisible, because the use-case tests that depend on the fake stay
green while the adapter behaves differently in production. That is the failure
this rule exists to prevent, and it is why MockK cannot substitute: ADR-043 already
notes that a `mockk()` double does not implement the port at all.

## Why the name changed

The rule was `port-has-contract-suite` and looked for `<Port>Contract`. The term
"contract suite" is not standard, and it collides with a different established
meaning: a contract test in Fowler's sense is a consumer-driven agreement between
two deployed services, which is not this at all. `Conformance` is the term used
for "every implementation must conform to one specification", and it does not
collide with anything.

The word `contract` was also already load-bearing in this repository as a *layer*
name — `contract-is-self-contained`, `contract-is-immutable`, `contract-no-logic`
— so a `JobsContract` in a `..port..` package read as though it belonged to the
`contract` layer, which it does not.

The rename covers the rule id, the type suffix the rule looks for, the fixture
directory, the rule catalogue in ARCHITECTURE.md §15.3, and the prose in ADR-043
and ADR-044. The layer rules are untouched.

## What the rule does and does not check

It checks that a type named `<Port>Conformance` exists. It cannot check that the
suite is meaningful, that it is actually run against both implementations, or
that it covers the port's whole surface. Those rest on review.

What does hold mechanically is the property ADR-044 relies on: the fake
implements the port in full, so adding a method to the port breaks the fake at
compile time and the suite gains a case that neither implementation passes until
both do.

## Rejected alternatives

**Keeping `contract suite`.** The name was invented for this repository rather
than borrowed, and it misdirects a reader who knows the industry meaning. Renaming
cost one commit on an empty tree; renaming after twenty ports exist would not have
happened.

**`<Port>Spec`.** Every Kotest class here ends in `Spec`, so the suffix carries no
information about the obligation and the rule could not distinguish a conformance
suite from any other test.

**Dropping the rule.** Considered, since the concept had no definition anywhere
and an undefined rule is worse than none. Rejected because the underlying problem
— a fake that quietly stops matching the adapter — is real, cheap to prevent and
expensive to find later.
