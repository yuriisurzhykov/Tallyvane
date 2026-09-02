# ADR-074. `platform:cache` ships only `Counter`, in memory, until a real value cache has a consumer

## Decision

`platform:cache` starts with one port, `Counter` — "how many times did this happen inside a
moving window" — and one implementation, `Counter.InMemory`, backed by nothing but a
`ConcurrentHashMap` and `platform:kernel`'s `Clock`. A second, general-purpose value cache (get,
put-with-a-deadline, get-or-compute) is not built in this pass.

Whether a counter store being unavailable should fail open (let every attempt through) or fail
closed (refuse every attempt) is explicitly *not* decided by this port or this ADR. `Counter`
throws whatever `ConcurrentHashMap.compute` throws — nothing, in practice, short of an `Error` —
and a caller wrapping a real network-backed implementation decides what an exception means for its
own operation. The first real caller, `identity`'s login and second-factor rate limiting, is to
fail closed: refuse the attempt rather than let it through unguarded. That choice belongs to the
slice that writes the guard, not to this one, and is recorded here only so it is not lost between
the two.

## Why one port, not two

`backend/.plans/backend-infra-cache-wiring.md` proposed two ports on the grounds that a cache and a
counter fail differently — losing a cached value costs latency, losing a counter changes behaviour
— and that reasoning still holds. It does not yet justify building both: nothing in this pass reads
or writes a cached value. The two named candidates in that file — LLM response caching and a
session-lookup cache inside `identity`'s own `PrincipalResolver` — are both real, and both still
several slices away (`platform:llm` does not exist; `PrincipalResolver`'s real implementation is
step 14 of `identity`'s own build order). A `Cache<V>` port with no caller is a shape guessed at
from outside the code that would use it, which is exactly the risk ADR-046's port-conformance
suites exist to catch late rather than never: an abstraction is only as good as the real
implementation and the real consumer that between them prove it fits. `Counter` ships alone because
it has a real, immediate consumer — `identity`'s rate limiting, in the very next slice — and `Cache`
returns to this file's "Not here" section, not to a speculative interface today.

## Why in memory, not Redis

One instance of the application runs today, so a counter that only ever needs to be right on that
one instance loses nothing by living in its process — a restart or a redeploy resets every
rate-limit window to zero, which only ever makes the next attempt look like the first one again,
the safe direction for a mistake to fail in. `backend/.plans/backend-infra-cache-wiring.md` names
the two conditions that would retire this for a shared store: surviving a restart (an attacker's
progress toward a threshold reset for free is an acceptable cost; a legitimate cache spending money
per lookup is not, which is `platform:llm`'s future argument, not this one's), or more than one
instance of the application running at once, sharing one counter. Neither holds yet. Reaching for
Redis before either does is paying for a network round trip and an external process to solve a
problem this module does not have.

## Why the failure behaviour is named here and decided later

`Counter.InMemory` cannot itself be "unavailable" in any sense worth designing around — it is a map
in the same process as its only caller, and the only way it fails is the JVM itself failing. The
fail-open-versus-fail-closed question `backend/.plans/backend-infra-cache-wiring.md` raises and
calls "bad in its own way either direction" is a real question, but it is a question about a
network-backed implementation this pass does not build, asked of a caller — the rate-limiting
decorator around a sign-in or second-factor use case — that does not exist yet either. Deciding it
now would be answering a question the code cannot yet ask. Recording the answer now, ahead of the
code that will need it, is what keeps that slice from re-opening a question already settled: fail
closed. An unavailable rate-limit store means refusing every attempt until it recovers, not
admitting every attempt unguarded — the account-lockout mechanism is a piece of the system's
security posture, and its going dark should read as "sign-in is down", loud and visible, rather
than as a silent gap nobody notices until it is exploited.

## Rejected alternatives

**Building the general `Cache<V>` port alongside `Counter` now, since the design conversation
already worked out its shape.** A port designed before its first real caller is precisely the
mistake `backend/.plans/backend-infra-cache-wiring.md` itself calls out — "Что кэшируем первым:
честно, пока нечего" — and building it anyway would be choosing the shape from the outside once
more, the same habit that produced the two-class sketch of "how a module knows the user" the
project's own author already rejected once for `identity`.

**Deciding fail-open for the login-rate-limit case, on the grounds that an in-memory store is
extremely unlikely to fail.** True today, but the decision is being made once, for whatever
implementation eventually backs this port — including a future Redis-backed one, which *can*
genuinely become unavailable over a network. Tying the decision to today's implementation instead
of to what the guard is *for* would mean re-deciding it the day the implementation changes, silently,
possibly by whoever happens to be swapping the adapter and not thinking about rate-limit policy at
all.

**Reaching for Redis now, on the grounds that `identity`'s rate limiting is security-sensitive and
"important enough" to warrant a real external store from day one.** Sensitivity is not the same
question as "does this specific property — surviving a restart, or being shared across instances —
matter yet", and neither holds today. The in-memory implementation gives the exact same security
property (a bounded number of attempts inside a window) with a strictly smaller footprint: no new
service in `ops/docker-compose.yml`, no new failure mode to design around before the port has even
been used once.
