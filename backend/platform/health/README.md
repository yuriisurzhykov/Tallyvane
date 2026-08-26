# platform:health

The three health addresses, and the only place the shape of an answer is decided.

```
GET /api/v1/health         aggregate; one field without the service token, the breakdown with it
GET /api/v1/health/live    is the process alive; consults nothing
GET /api/v1/health/ready   should traffic come here; 200 or 503
```

Decisions: [ADR-063](../../../docs/adr/ADR-063-health-endpoints.md), on top of
[ADR-054](../../../docs/adr/ADR-054-health-check-shape.md) (what a check is) and
[ADR-055](../../../docs/adr/ADR-055-health-response-shape.md) (what an answer says).

## Why a module of its own, when nothing existing would do

This is the first platform module that publishes endpoints, and the placement is the decision the
slice turned on. A health route needs `RouteModule` from `platform:http`, `HealthReporter` from
`platform:observability`, and serialization — and each of the existing homes charges for it:

`observability` would gain a dependency on `http`. That module is depended on by everything that
produces a signal, which works only while it depends on no capability itself; the edge would end that.

`http` owns the *mechanism* — routing, the error contract, the trace. Giving it particular endpoints
invites every later endpoint to follow, and then the mechanism module knows the product.

`app` is barred by `app-has-no-logic`: only `*Wiring`, `*Configuration` and `*Application` may live
there, and a route class is none of those.

So: a small module that depends on both and that nothing depends on except `app`. Metrics will arrive
with the same question, and this is the answer they will reuse.

## The three addresses, and why they differ

**Liveness consults nothing** — and is not handed a reporter to consult, so it cannot start to. A
liveness probe that checks a dependency converts that dependency's outage into a restart loop of a
healthy process: the database goes down, the probe fails, the orchestrator restarts an application
that was working, again and again, and one outage becomes two. `HealthRoutesSpec` asserts the reporter
was asked **zero** times, and that case was verified by making liveness consult the reporter and
watching it fail.

It is also the most frequent probe, so free matters: an aggregate costs 85 ms for the database plus
147 ms for the schema when warm, measured in `playground/health`, and an orchestrator asks every few
seconds.

**Readiness answers with a code**, because that is all an orchestrator reads. `ready` comes from
`requiredForReadiness`, not from the aggregate status, so a `degraded` application answers **200** —
an unavailable model must not close the two thirds of the product that needs no model. That is what
three states are for, and a naive implementation that maps `degraded` to 503 takes a working instance
out of service. 503 rather than 500 for unready: 503 means "not now, try later", which is the
statement being made.

**The aggregate is read by a human**, so it always answers 200 and carries meaning in the body. An
informational endpoint returning 503 looks to a monitor like the endpoint itself being down.

## Two shapes, two types

`Summary` has one field. `Detail` has the breakdown. They are different types rather than one type
filtered, and that is the point: filtering puts the decision at every call site, and one forgotten
filter publishes the list of everything the system depends on and which part is currently broken —
reconnaissance for anyone looking for somewhere to push. A type with one field cannot leak a second.

`Cause` is the wire form of `Ailment`, discriminated by `kind` (`ApiJson` sets that globally; ADR-055
explains why not `type`). It has **no case** for `Ailment.Dependencies` or `Ailment.Behind`: those
name the system's parts and its schema versions, which ADR-055 withholds from every answer including
an authorised one. Their absence is structural — there is no type that could render them — rather than
a filter someone has to remember. A test pins it anyway.

Separate DTOs also keep the serialization library out of `observability`, and leave `Ailment` free to
grow a field without changing a contract slice 14 will describe.

## The service token, and when it goes away

ADR-055 gives the breakdown to an authorised reader, and §11.2's authentication arrives with
`identity`, which does not exist. Waiting for it would mean the half a human needs during an incident
arrives last.

So: one secret from configuration, in `X-Service-Token`, compared in constant time. Not
`Authorization` — this is not a user's credential, and using that header would invite a later reader
to treat it as one, as well as spending the header the real mechanism will want. A caller without the
token gets the public answer rather than an error, because refusing would tell an anonymous caller
that there is something to refuse.

Two properties worth stating because both are easy to get wrong:

**Constant-time comparison.** `==` on strings returns at the first differing character, so its
duration leaks how much of a guess was right — enough to recover a secret one character at a time. The
comparison here reads every character whatever it finds. The length is compared first and does leak;
hiding it would mean hashing both sides on every probe, and a length is not the secret.

**An unset secret admits nobody**, including an empty header. The failure mode of a forgotten setting
is no access, never all access. Pinned by a test.

This mechanism is temporary, and ADR-063 says so in the decision itself. A mechanism introduced
because a dependency is missing has to name its own expiry, or silence makes it permanent.

## Nothing here is cacheable

`Cache-Control: no-store` on all three answers. Cloudflare sits in front of the application (§16.3),
and a 200 with no cache directives is a legitimate thing to cache — after which a cheerful "up" would
outlive the truth. Pinned across all three paths by one test.

## Fault tolerance

The reporter is asked once per request and its failure is not caught here: `Bounded` and `Contained`
already bound each check and turn a throw into `Down` with the exception's type and nothing else
(ADR-054), so this module has nothing to add and no reason to swallow anything. An escaped failure
still becomes a 500 in RFC 9457 shape through `platform:http`'s chain.

There is one number this module cannot enforce and it belongs in the deploy file: **the orchestrator's
timeout must exceed the bound applied to a check.** Otherwise the orchestrator concludes "no answer"
before the application can say "this dependency is slow", and a degradation is answered with a
restart.

## SOLID

**Single responsibility.** `HealthRoutes` maps three addresses to three behaviours; `Presented` turns
one report into two shapes; `ServiceToken` answers one question about one header; each DTO is a wire
form. The mapping is a class rather than companion factories because it branches, and
`no-companion-logic` forbids a companion that decides — rightly, since a deciding factory is a
decision nobody reviews.

**Open/closed.** A new check appears in the breakdown with no change here; a new `Ailment` case
appears only if `Cause` gains a case, which is where the decision about disclosure belongs.

**Liskov.** `HealthReporter` is consumed through its interface, and the test substitutes a fake that
also counts calls — which is how "liveness consults nothing" became assertable.

**Interface segregation.** This module implements `RouteModule`, two members, and consumes
`HealthReporter`, one. Nothing here implements a method it does not need.

**Dependency inversion.** It knows `HealthReporter` and `RouteModule`, not `OverChecks`, not
`DatabaseAnswers`, not Ktor's engine. Which checks exist is `app`'s knowledge; this module would serve
a reporter over anything.

## Not here

The list of checks, the scope `Bounded` abandons work into, the per-check bounds and the token's value
are all `app`'s, in slice 13. The OpenAPI description is slice 14. Real authentication is §11.2 with
`identity`, and it replaces the token.
