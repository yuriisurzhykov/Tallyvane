# ADR-068. The differentiating layer enters the unified milestone sequence, not a separate phase

## Decision

Career Memory, Career Graph, matching, Opportunity Score, Next Best Action, Outcome Learning, personal experiments and
voice — previously "Phase 2, Milestones 10–18," gated behind an explicit, separately recorded decision to turn the tool
into a product (§1.4, the old §21 header) — move into the same milestone sequence as everything else (§21), starting as
early as Milestone 1. No decision to "become a product" is required to build any of it.

## Why

Real access to these capabilities is not a business question. Career Memory is valuable to a single user exactly as
much as job tracking is: without a base of facts about the person, a recommendation to apply somewhere is unsupported
by anything. The old gate conflated two different questions — "is this worth building now" and "is this a business
with more than one customer" — under one label, and answered the first with the second. The milestone order already
tracks the real constraint (which milestone's output another milestone needs as input); the phase gate added a second,
unrelated condition on top of that ordering, and this decision removes the second condition while keeping the first.

Building the simple version of an algorithm first and the sophisticated one later is not new practice introduced by
this decision — §20 already describes swapping `interviewOdds`'s heuristic for a trained model as a one-line port
replacement. This decision applies the same idea to *when the module ships*, not only to what replaces its internals
afterward: `matching` ships in Milestone 5 with tag overlap, not with embeddings, and the port stays the same when
embeddings arrive.

## Rejected alternatives

**Leave the phase gate, just rename its trigger to something other than "turn into a product."** Considered, since the
objection could in principle be to the label rather than to gating at all. Rejected because the actual objection was to
gating itself: a renamed gate that still exists produces the identical conversation the next time someone asks why
Career Memory is not built yet, just with different wording attached to the same blocking condition.

**Collapse every milestone into one flat, unordered list with no distinction at all.** Rejected: the milestones retain
a real order — which one's output another one needs as input — and removing that structure entirely would discard true
information along with the unwanted gate. §21's "later does not mean less important" section exists precisely to keep
the ordering visible without reintroducing a phase label.

**Keep a "Phase 2" heading only for the handful of items that remain genuinely late (offer comparison, gamification).**
Rejected: it reintroduces the same ambiguity for a smaller set. A reader would still have to ask why exactly these items
get a phase label and the rest do not, and the honest answer differs per item — some wait on data volume, others are
simply lower priority — so a single shared label over a subset would misrepresent at least one of them.
