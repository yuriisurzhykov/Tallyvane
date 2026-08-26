# ADR-063. Health endpoints live in their own platform module, behind a service token

## Status

Accepted. Built as `platform:health` with fourteen cases behind it.

## Context

[ADR-054](ADR-054-health-check-shape.md) settled what a check is, and
[ADR-055](ADR-055-health-response-shape.md) settled what an answer looks like. Slice 12 had to serve
those answers, and the plan predicted no decisions. It contained four.

**Where the routes live.** A health route needs three things at once: `RouteModule` from
`platform:http`, `HealthReporter` from `platform:observability`, and serialization. No existing module
can hold all three without a cost. Putting them in `observability` makes it depend on `http` — and
`observability` is the module every producer of signals depends on, which only works while it depends
on no capability itself. Putting them in `http` makes the module that owns *mechanism* own particular
endpoints, which invites every future endpoint to follow. Putting them in `app` is forbidden by
`app-has-no-logic`, which allows only `*Wiring`, `*Configuration` and `*Application` there.

**Authorisation, which does not exist.** ADR-055 gives the breakdown to an authorised reader only.
§11.2's authentication arrives with `identity`, which is not built. So either the detailed answer
waits for a module several slices away — the most useful half arriving last — or something else
guards it.

**Serialization.** `Health`, `Ailment` and `HealthReport` carry no annotations, and
`platform:observability` has no serialization library outside its tests.

**The status codes.** An orchestrator reads the code and nothing else. What code a `degraded` but
ready application returns decides whether a working instance is taken out of service.

## Decision

**A new module, `platform:health`.** It depends on `kernel`, `observability` and `http`; nothing
depends on it but `app`. That keeps `observability` free of any capability — the property that lets
every other platform module depend on it — and keeps `http` a mechanism rather than a home for
endpoints. It also settles the pattern for what comes next: metrics will ask the same question, and
the answer will be the same shape.

**A service token from configuration, compared in constant time.** A single secret the deploy
supplies, sent in `X-Service-Token`; without it the caller gets the public answer, not an error. Not
`Authorization`, because this is not a user's credential and naming it so would invite a later reader
to treat it as one. The precedent is `admin.tallyvane.com` behind Cloudflare Access: a service
boundary of its own rather than a user session. **Expected to be replaced** when `identity` exists.

An unset secret admits nobody — including an empty header. The failure mode of a forgotten setting is
no access, never all access.

**Separate DTOs, in this module.** `Summary` has one field; `Detail` has the breakdown; `Cause` is the
wire form of `Ailment`, discriminated by `kind`. Two reasons, and the first is the load-bearing one:
`Summary` and `Detail` being different *types* means the public answer cannot grow a field by
accident. Filtering one rich object down would put that decision at every call site, and one
forgotten filter publishes the list of everything the system depends on.

The second is direction: annotating `Ailment` itself would put a serialization library into
`observability`, and the wire form is a contract slice 14 describes while `Ailment` is free to grow.

`Cause` has **no case** for `Ailment.Dependencies` or `Ailment.Behind`. They name the system's parts
and its schema versions, which ADR-055 withholds from every answer including an authorised one. There
is no type that could render them, so no filter has to remember not to.

`ApiJson` gains `classDiscriminator = "kind"`, which ADR-055 had already decided and nothing had yet
implemented.

**Codes: 200 for the aggregate always; 200 or 503 for readiness; 200 for liveness.** Readiness is
computed from `requiredForReadiness`, so `degraded` answers 200 — the whole point of three states is
that an unavailable optional dependency does not close the product. 503 rather than 500 for unready,
because 503 means "not now, try later" and that is exactly the statement. The aggregate is read by a
human and always answers 200: an informational endpoint returning 503 looks to a monitor like the
endpoint itself being down.

**Liveness consults nothing, and is not given a reporter to consult.** A liveness probe that checks a
dependency turns that dependency's outage into a restart loop of a healthy process. It also makes the
most frequent probe free — measured in `playground/health`, an aggregate costs 85 ms for the database
plus 147 ms for the schema when warm, and an orchestrator asks every few seconds.

**`Cache-Control: no-store` on all three.** Cloudflare sits in front of the application (§16.3), and a
200 with no cache directives is a legitimate thing to cache. The failure that would follow is a
cheerful "up" served from a cache while the application is down.

## Consequences

`app` wires three things into one route module: the reporter, the token, and the list. Slice 13 owns
the token's source and the per-check bounds.

The orchestrator's own timeout must exceed the bound applied to a check, or it will conclude "no
answer" before the application can say "this dependency is slow" — a restart where a degradation was
the truth. That number lives in the deploy file and is named here so it is not discovered later.

An unauthorised caller can still learn the aggregate status, which is deliberate: an orchestrator
needs it and it discloses nothing about composition.

Adding a wire case for a new `Ailment` means editing `Cause` — visible, and the right place for a
decision about what the API discloses.

The token will be replaced. That is recorded rather than hidden: a mechanism introduced because a
dependency is missing must say when it expires, or it becomes permanent by silence.

## Alternatives considered

**One endpoint with a query parameter for detail.** Rejected: the parameter would be the only thing
between a caller and the system's composition, and a parameter is easier to leave off a review than a
header check is.

**Serving the breakdown to everyone until the system is no longer personal.** Rejected by ADR-055
already: the endpoint is reachable from the internet through the tunnel, and withholding costs
nothing because an orchestrator does not read the body.

**Waiting for `identity` before serving the detailed answer.** Coherent, and rejected on usefulness:
the breakdown is what a human needs during an incident, and incidents do not wait for slice 15.

**Reusing `Authorization: Bearer`.** Rejected because a future reader would reasonably assume a user
identity behind it, and because the eventual replacement will want that header for its real purpose.

**Caching the aggregate for a few seconds** to spare Flyway a connection per probe. Deferred rather
than rejected: it needs a measured number under load, and a cached probe is a class of change where
"up" outliving the truth is the failure mode.

**`application/health+json` and `pass`/`fail`/`warn` from the IETF draft.** Rejected because ADR-055
already fixed our own shape with `up`/`degraded`/`down`, and two vocabularies for one thing is worse
than either.
