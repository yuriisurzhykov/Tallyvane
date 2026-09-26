---
name: fail-fast-and-observability
description: >-
  A checklist to run on every function or method before calling it done: does
  it have a constraint the caller must respect, should that constraint exist,
  is it enforced as a compile error or a runtime exception naming exactly what
  the caller forgot, does a genuinely new kind of failure get its own type
  instead of a reused generic one, and — where none of that is clear-cut — is
  the non-obvious path at least logged. Also fixes the shape a diagnostic log
  line takes, so a debugging session can reconstruct what came in, what was
  decided, and what happened. Use when writing or reviewing a Kotlin backend
  function or a TypeScript frontend/package function, or when deciding whether
  a case needs a precondition, a new exception type, or a log line.
---

# Fail-fast and observability

## 1. What this is for

A function can be wrong about a case nobody thought about in two different
ways: it can do the wrong thing quietly, or it can refuse to run at all. This
skill is the check to run on a function before calling it done, so a wrong
case ends up in the second bucket — as close to a compile error as it can
get, or, failing that, a runtime exception that names exactly what was
missing.

[`backend/ENGINEERING-PRINCIPLES.md`](../../../backend/ENGINEERING-PRINCIPLES.md)
already states the Kotlin half of this at the type-design level — "Failure
is a value inside and an exception at the edge", "A message names the next
action". Read it; this skill does not restate it. What it adds: the question
to ask about a single method's own body (not a whole type's contract), the
TypeScript equivalent — nothing in this repository states that yet — and the
shape a log line takes so a debugging session can follow what happened
without re-running the code.

## 2. The four questions

Ask these about every branch of a function before moving on, not once at the
end of writing it.

**2.1 Does this method assume something about its input, the caller's
state, or the order it is called in?** An index that must be in range, a
list that must be non-empty, a resource that must already be open, two
arguments that must agree in length. Most functions have at least one of
these, and most of the time nobody wrote it down.

**2.2 If nothing enforces that assumption right now — should it?** The test
is what happens when a caller gets it wrong: does the function produce a
value that looks fine and is wrong (a `0` where there should have been no
answer, a silently truncated list), or does something stop it? A value that
looks fine and is wrong is the case worth closing.

**2.3 If it should be enforced, rank the way it fails, and pick the
strongest one reachable.** A compile error beats everything: a type that
cannot hold the invalid state at all, an exhaustive `when`/`switch` with no
fallback branch. Where that is not reachable, it is a runtime exception —
and here there are two shapes, not one, and picking between them is part of
the question rather than a detail to settle later.

A failure that is genuinely new — a kind this codebase has not named before,
one a caller might reasonably want to catch and handle differently from
everything else that throws — gets its own type: a new case in an existing
sealed outcome if this is an in-layer failure a caller is expected to
handle, or a new named exception class if it only belongs at a boundary.
Reaching for an existing generic exception and writing a longer message
around it is how a recurring, distinct failure stays invisible to both the
type system and to `catch` blocks that would otherwise be able to name it.

A failure that is not a new kind — an argument out of range, a precondition
the function's own contract already implies — does not need a new type,
only a message that is the deliverable: it names what the caller forgot
(which condition to check, which case to handle, which precondition to
satisfy), not just what state was found. `"invalid index"` describes the
failure; `"index 7 is out of range for a list of 3 — did the caller forget
to re-check size after the delete above?"` describes the mistake.

**2.4 If neither 2.1–2.3 produced a crisp answer, but something specific in
this method's body can plausibly go wrong** — a call to something outside
the process, a boundary value that is legal but rare, a fallback path — that
gets a log line even without a precondition around it. A method with no
failure mode anyone can name yet is exactly the one that fails silently in
production with nothing to read afterward.

## 3. Encoding a constraint in Kotlin

The type-design version of this already exists in this repository and is
not restated here. Read
[`backend/ENGINEERING-PRINCIPLES.md`](../../../backend/ENGINEERING-PRINCIPLES.md)'s
"Failure is a value inside and an exception at the edge" and "A message
names the next action" sections, and
[ADR-062](../../../docs/adr/ADR-062-error-contract.md) for why a sealed
`Failure` root plus an exhaustive `Problems<F>.of` turns a forgotten failure
case into a compile error rather than a silent 500. `TraceId`/`SpanId` in
`platform:observability` validate in `init`, so an invalid identifier cannot
exist rather than being checked at every call site — the same move works for
any value object with a real constraint.

What that document does not cover is a single method's own local branching,
which is where question 2.3 actually lands day to day:

- A local `when` over a sealed type or enum, with no `else` — a new case
  breaks the build at every place that switches on it, instead of silently
  falling into a default that was written for the cases that existed at the
  time.
- `require()` at the top of a function for an argument precondition,
  `check()` partway through for an invariant the function's own logic should
  have kept true. Both take a lazy message; write the one that names the
  fix, following `ENGINEERING-PRINCIPLES.md`'s own example rather than a new
  phrasing:

  ```kotlin
  require(templates.isNotEmpty()) {
      "No resume template in ${dir.absolutePath}. " +
          "Run ./gradlew :modules:resume:seedTemplates before rendering."
  }
  ```

- `firstOrNull()`/`singleOrNull()`, handled explicitly, beats
  `first()`/`single()` — the latter throws `NoSuchElementException`, which
  carries no information about which precondition the caller skipped.

**When the failure itself is new**, which type it becomes depends on where
it lives, and `ENGINEERING-PRINCIPLES.md`'s own split answers it: an
in-layer failure a use case's caller is expected to handle is a new case in
that use case's own sealed outcome — a value, not a throw — exactly the move
ADR-062 records happening for real: a malformed request was answering the
same 500 as a genuine outage until a seventh meaning, `malformed`, was added
to `Answers` because 400 ("I could not understand you") turned out to be a
different statement from 422 ("I understood and refused"), not a detail
worth folding into an existing case's message.

A failure nothing inside foresaw — a driver refusing, a pool exhausted, a
lock timeout, an actual bug — is the one that reaches an exception, and it
is a *named* one:

```kotlin
// Before: a distinct, recurring failure hiding inside a generic exception's
// message. A caller that wants to react to exactly this case has nothing to
// catch except IllegalStateException, which also matches every other bug.
check(session.isActive) { "Session ${session.id} is not active" }

// After: named once, so every caller that cares catches this type instead
// of re-parsing a string, and every caller that does not still sees a
// message naming the fix.
class InactiveSessionException(sessionId: SessionId) :
    IllegalStateException("Session $sessionId is not active; caller must re-authenticate before retrying")
```

The generic `IllegalStateException`/`IllegalArgumentException` plus a
`require()`/`check()` message from the previous bullet is still correct for
a failure that is *not* new — an argument out of range, a precondition
this function already implies. Giving every `require()` its own exception
subclass would be the opposite mistake: a type for something nobody will
ever catch differently is ceremony, not safety.

## 4. Encoding a constraint in TypeScript

Nothing in this repository states this yet outside one narrow case:
[`component-authoring/SKILL.md`](../component-authoring/SKILL.md) §3.6
already requires exhaustiveness checking (`default: never` in a `switch`)
on a component's discriminated prop. The same move applies to any function
that switches on a discriminated union, not only component props:

```typescript
function assertUnreachable(value: never): never {
  throw new Error(`Unhandled case: ${JSON.stringify(value)}`);
}

switch (outcome.kind) {
  case "ok":
    return outcome.value;
  case "notFound":
    return null;
  default:
    return assertUnreachable(outcome);
}
```

Adding a third member to `outcome`'s union without adding its case here is a
compile error at the `assertUnreachable(outcome)` call, not a case that
silently falls through. That is the TypeScript shape of "a new in-layer
failure is a new case in a value, not a throw" — the same move the Kotlin
section above points at ADR-062 for.

**When the failure itself is new** and genuinely belongs at a boundary — a
fetch that failed, a state a caller might want to catch and handle
differently from any other thrown value — give it its own subclass instead
of a generic `Error` with a longer message:

```typescript
// Before: a distinct, recurring failure hiding inside a generic Error's
// message. A caller that wants to react to exactly this case has nothing
// to catch except Error itself, which also matches everything else that throws.
if (!session.isActive) {
  throw new Error(`Session ${session.id} is not active`);
}

// After: named once, so every caller that cares catches this type instead
// of re-parsing a string, and every caller that does not still sees a
// message naming the fix.
class InactiveSessionError extends Error {
  constructor(public readonly sessionId: string) {
    super(`Session ${sessionId} is not active; caller must re-authenticate before retrying`);
    this.name = "InactiveSessionError";
  }
}
```

Two things this repository has not built yet, so this skill names the gap
instead of inventing an answer: a shared `invariant(condition, message)`
helper — the TypeScript equivalent of `require`/`check`, for the failures
that are *not* new and only need a message — and a small shared `Error`
subclass hierarchy, so new classes like `InactiveSessionError` share a base
that marks them as "a bug in our own code" rather than each standing alone.
Per the workspace
[`development-methodology`](../../rules/development-methodology.mdc) rule's
"will this be reused?" test, either belongs in
`packages/frontend-shared/src/shared/lib/` the first time real application
logic needs one — not added speculatively by this skill. Until then, a
one-off subclass defined next to its call site, as above, is still better
than a generic `Error` carrying a distinct failure's whole identity in a
string.

## 5. Logging so a debugging session can reconstruct the story

A log line is worth writing when it answers one of three questions: what
came in, what was decided, and what happened as a result. Three lines around
a branch, not one line saying `"done"`, is what lets a reader who was not
there rebuild the request without re-running it.

**Backend.** Already decided (`platform:observability`,
[ADR-056](../../../docs/adr/ADR-056-request-identity.md)): JSON, one object
per line, `trace_id`/`span_id` attached automatically by `TraceContext`
across every coroutine suspension, level chosen by audience — `WARN` means a
human needs to look, not that something merely unusual happened. Follow that
shape:

```kotlin
log.info { "extractSkills: received ${resumes.size} resumes for jobId=$jobId" }
// ...
log.info { "extractSkills: confidence=$confidence, branch=${if (confidence >= threshold) "accept" else "fallback"}" }
// ...
log.warn { "extractSkills: confidence $confidence below threshold $threshold, falling back to manual review" }
```

**Frontend.** No equivalent decision exists yet — there is no shared logger
and no chosen destination beyond the browser console, and whether one is
ever needed (a client error-reporting service, an ingestion endpoint) is an
open question this skill does not settle. Until that decision is made, the
same three-line shape still applies to `console.debug`/`console.warn`, with
structured context rather than an interpolated sentence:

```typescript
console.debug("[useJobsQuery] fetch:start", { jobId });
// ...
console.debug("[useJobsQuery] fetch:result", { status, count: jobs.length });
// ...
console.warn("[useJobsQuery] falling back to cached list", { reason: error.message });
```

## 6. Checklist before calling a method done

- [ ] Named, for every branch, what the method assumes about its input, the
      caller's state, or call order.
- [ ] For each assumption not yet enforced: decided on purpose whether it
      should be, not left implicit.
- [ ] Every enforced assumption fails as a compile error if at all possible;
      otherwise a runtime exception whose message names the missing
      check/case/precondition, not just the broken state.
- [ ] Every genuinely new kind of failure — one nothing in the codebase has
      named yet — got its own sealed case or exception type, not a reused
      generic exception with a longer message.
- [ ] Every branch that is not obviously safe but has no crisp precondition
      around it carries at least one log line.
- [ ] A reader of the logs alone — no debugger, no re-run — can tell what
      came in, what was decided, and what happened.

## 7. Worked example

A function choosing the cheapest of several quotes, with an empty list
nobody planned for.

```kotlin
// Before: silently wrong. An empty list returns null, indistinguishable
// from "every quote was somehow null", and the caller finds out three calls
// later.
fun cheapest(quotes: List<Quote>): Quote? = quotes.minByOrNull { it.amount }

// After: the missing case is named where it happens.
fun cheapest(quotes: List<Quote>): Quote {
    require(quotes.isNotEmpty()) { "cheapest() needs at least one quote; caller must filter before calling" }
    return quotes.minBy { it.amount }
}
```

```typescript
// Before: same shape, same problem.
function cheapest(quotes: Quote[]): Quote | undefined {
  return quotes.reduce((min, q) => (!min || q.amount < min.amount ? q : min), undefined as Quote | undefined);
}

// After.
function cheapest(quotes: Quote[]): Quote {
  if (quotes.length === 0) {
    throw new Error("cheapest() needs at least one quote; caller must filter before calling");
  }
  return quotes.reduce((min, q) => (q.amount < min.amount ? q : min));
}
```

Both versions still want a log line around the call site if an empty list is
a real possibility in production rather than a pure programmer error —
question 2.4, not 2.3: something worth knowing happened, even though it is
not a broken precondition.

A second example, for the other half of 2.3 — a failure that is not a
missing precondition but a new kind nobody named yet, reusing the
`InactiveSessionException`/`InactiveSessionError` pair from sections 3 and
4:

```kotlin
// Before: the same generic exception every other invariant in this file
// throws, so a caller cannot tell this failure apart from any other bug.
fun renew(session: Session) {
    check(session.isActive) { "Session ${session.id} is not active" }
    session.extend()
}

// After: a caller that wants to redirect to sign-in on exactly this failure
// now has a type to catch instead of a string to parse.
fun renew(session: Session) {
    if (!session.isActive) {
        throw InactiveSessionException(session.id)
    }
    session.extend()
}
```

```typescript
// Before.
function renew(session: Session): void {
  if (!session.isActive) {
    throw new Error(`Session ${session.id} is not active`);
  }
  session.extend();
}

// After.
function renew(session: Session): void {
  if (!session.isActive) {
    throw new InactiveSessionError(session.id);
  }
  session.extend();
}
```
