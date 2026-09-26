---
name: doc-comment-quality
description: >-
  A reasoning checklist for writing or reviewing a class/interface/method
  doc comment (KDoc, JSDoc, TSDoc, JavaDoc): whether a sentence describes
  behavior or defends a design choice, whether a worked example earns its
  place or just restates the prose above it, whether an example's values
  would actually pass the type's own runtime checks, and whether a
  multi-method type's methods each carry their own contract. Use when
  writing a new public class/interface/function's doc comment, or when a
  doc comment reads as a design narrative — citing ADRs, comparing to other
  classes, defending a choice at length — instead of an API reference.
---

# Doc-comment quality

## 1. What this is for

[`comment-style.mdc`](../../rules/comment-style.mdc) already states the
mechanical rules — when a worked example is warranted, what a
multi-method type's method comments look like, that rationale beyond one
clause belongs in a README, not the comment. Read it; this skill does not
restate it. What it adds is the question to ask about a real comment being
written right now, and three worked before/after pairs from the session
that produced those rules: a comment that had become a design journal
(`LoginAttemptsOverCounter`), a comment that invented an example the type
didn't need (`TokenPair`), and a comment split correctly across a
class-level and a method-level doc (`LoginAttempts`).

## 2. The question to ask about every sentence

For every sentence going into a doc comment: **does it describe behavior —
what is accepted, what comes back, what is thrown, when — or does it
defend a choice, comparing this design to an alternative that was not
taken?**

- Behavior stays, however much of it there is. `comment-style.mdc`'s
  exception for doc comments exists exactly so this can be thorough.
- A defended choice earns one clause at most. Longer than that — a
  rejected alternative, an ADR reference, a comparison to another class, a
  wrong-turn story — moves to the module's README, with one line in the
  comment pointing at it.

### Before: a design journal, not an API reference

```kotlin
/**
 * [LoginAttempts] over `platform:cache`'s [Counter] — the one file in this module allowed to
 * import [Counter], since `identity:application` may not (`modules.yaml`; see [LoginAttempts]'s
 * own KDoc for why the port exists at all).
 *
 * A thin, faithful relay: whatever [Counter] does — including throwing when its store is
 * unavailable — reaches the caller unchanged, and this class does not catch, log or otherwise
 * react to that exception. Deciding what a failure *means*, including whether it is worth a line
 * in the log, is `RateLimited`'s job, not this adapter's — `ENGINEERING-PRINCIPLES.md`'s "A
 * recovered failure is logged where its meaning is known, not where it was thrown": a port
 * reports, it does not pre-empt the policy — logging policy included — for whoever reads it.
 */
```

Four sentences, all defending why the class is shaped this way, zero of
them telling a caller what `failuresWithin`/`recordFailure` actually do.

### After: the one behavior fact, the rest pointed at the README

```kotlin
/**
 * [LoginAttempts] over `platform:cache`'s [Counter] — throws whatever [Counter] throws, uncaught.
 *
 * Why this is the one file allowed to import [Counter], and why it never catches or logs:
 * `infrastructure/README.md`.
 */
```

## 3. Does this comment need a worked example?

Apply `comment-style.mdc`'s own test: would removing the example delete a
fact about behavior the prose above it does not already state?

### Before: an example that repeats prose, with a value the type rejects

```kotlin
/**
 * The two tokens issued together at sign-in or refresh.
 *
 * ```
 * val pair = TokenPair(access = TokenValue("access_..."), refresh = TokenValue("refresh_..."))
 * pair.access   // short-lived, presented on every request
 * pair.refresh  // long-lived, presented only to mint the next pair
 * ```
 */
data class TokenPair(val access: TokenValue, val refresh: TokenValue)
```

Both facts the example shows are already in one sentence. `"access_..."`
also fails `TokenValue`'s own shape check — the example would throw if run.

### After: no example, because the type has nothing to demonstrate

```kotlin
/**
 * The two tokens issued together at sign-in or refresh: the short-lived
 * [access] token presented on every request, and the long-lived [refresh]
 * token presented only to mint the next pair.
 */
data class TokenPair(val access: TokenValue, val refresh: TokenValue)
```

A plain data holder — stored properties, no `init` validation, nothing
computed — almost never earns an example. Name each property's role in one
sentence and stop.

### When an example does earn its place

`LoginAttempts.failuresWithin`'s window is genuinely ambiguous in prose —
counted from now, or from when each failure was recorded? An example
settles it, using values the type actually accepts:

```kotlin
/**
 * Failures recorded against [key] within the trailing [window] ending now.
 *
 * ```
 * attempts.recordFailure(key, 15.minutes)
 * attempts.failuresWithin(key, 15.minutes) // -> 1
 * // 16 minutes later:
 * attempts.failuresWithin(key, 15.minutes) // -> 0, window elapsed
 * ```
 */
suspend fun failuresWithin(key: String, window: Duration): Long
```

## 4. Class-level comment vs. method-level comment

A single-method interface — the class-level doc's own example already
documents the one method it has:

```kotlin
/**
 * Decides what to do with a presented refresh token: rotate if unseen, flag
 * the session for revocation if it has already been used.
 *
 * ```
 * decide(TokenFamilyState(sessionId, used = false)) // -> Rotate
 * decide(TokenFamilyState(sessionId, used = true))   // -> ReuseDetected(sessionId)
 * ```
 */
interface RefreshRotationPolicy {
    fun decide(family: TokenFamilyState): RefreshRotationDecision
}
```

A second comment on `decide` itself, repeating the same example, would be
noise — skip it.

A multi-method type gets one short line per method, plus `@param`/
`@return`/`@throws` for whatever a parameter's name does not already make
obvious on its own:

```kotlin
interface LoginAttempts {
    /**
     * Failures recorded against [key] within the trailing [window] ending now.
     *
     * @param key Identifies what is being rate-limited (an email, a pending-auth id).
     * @param window How far back from now to count.
     * @return Number of failures recorded against [key] inside [window].
     */
    suspend fun failuresWithin(key: String, window: Duration): Long

    /**
     * Marks one more failure against [key], counted starting now, expiring after [window].
     *
     * @param key Identifies what is being rate-limited.
     * @param window How long this one failure counts toward a future [failuresWithin] call.
     */
    suspend fun recordFailure(key: String, window: Duration)
}
```

## 5. Checklist before calling a doc comment done

- [ ] Every sentence describes behavior, or is a single clause defending a
      choice — nothing longer sits in the comment.
- [ ] Anything longer than one clause of rationale has a home in the
      module's README, with a pointer left in the comment.
- [ ] A worked example exists only if removing it would delete a fact
      about behavior the prose does not already state.
- [ ] Every value in an example would actually pass the type's own
      runtime checks — none of them are placeholders or ellipses standing
      in for a valid value.
- [ ] A single-method interface does not repeat its own example on the
      method; a multi-method type documents each method individually.
