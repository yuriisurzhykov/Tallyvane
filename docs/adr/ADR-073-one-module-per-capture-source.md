# ADR-073. Every `JobSource` implementation is its own Gradle module

## Decision

`capture:infrastructure` stops being one module holding every `JobSource` implementation as a sibling
class. Each implementation becomes its own leaf Gradle module instead — `capture:infrastructure:greenhouse`,
`:lever`, `:ashby`, `:smartrecruiters`, `:workday`, `:jsonld`, `:client-supplied` — with exactly one layer,
`infrastructure`, implementing the `JobSource` port owned by `capture:contract`/`capture:application`. The
rule applies uniformly to all seven implementations, including the two that are not tied to one external
platform (`JsonLdJobSource`, the generic `schema.org` fallback, and `ClientSuppliedJobSource`, the receiver
for extension-supplied content) — no exception carved out for them just because they read as more generic
than the five ATS adapters.

`app`, the composition root, depends on every leaf module and builds the `JobSourceRegistry` list from
them — unchanged from how it already assembles every other module's implementations today.

## Why

The case for this is not the one ADR-018 already made for layer boundaries. ADR-018 buys "a boundary
violation does not compile" for `domain`/`application`/`infrastructure`/`web` — directions where crossing
the line would let a use case reach a JDBC driver, or a route handler bypass validation. Two `JobSource`
implementations sitting as sibling files inside one `infrastructure` module do not have that problem
between them: `GreenhouseJobSource` cannot see `WorkdayJobSource`'s internals whether they share a module
or not, because Kotlin's `internal` visibility already scopes each class to its own file within the module,
and neither type has ever referred to the other. Reusing ADR-018's argument here would be citing the wrong
reason for a decision that needs its own.

The real argument is about the unit of change, not a boundary. `capture`'s source list is a registry
precisely because it is expected to grow (§20's whole premise for `JobSource` is "add a new ATS without
touching existing files") — the system context diagram already names four ATS platforms before a line of
`capture` exists. As the count grows, three costs accumulate under one shared module that a decomposed
module list avoids: touching one source recompiles and reruns the tests of every source sharing its module,
not just the one that changed; a source's dependencies (Workday's URL decomposition logic, a future
source's HTML parser, whatever the next one turns out to need) sit on the same classpath as every sibling
source whether or not that sibling needs them; and retiring a source that shuts down or stops being usable
means finding and deleting one file out of a shared module by hand, rather than deleting a module and
having the build graph confirm nothing else referenced it. None of the three is about compile-time safety
— they are about what "add a source" and "remove a source" cost as the list gets longer, which is exactly
the property `capture`'s own extensibility claim in §20 is measured on.

This is deliberately **not** generalized to every other Strategy/Registry point in the catalogue —
`NotificationChannel`, `LlmProvider`, `CompensationModel`, `ReminderRule` — in the same pass. Each of those
keeps today's "one class, one registry line" recipe. Generalizing on the strength of `capture`'s argument
alone would be assuming every registry has `capture`'s growth-and-divergence profile without checking:
`CompensationModel` gains a new employment type on the order of once, not continuously, and `NotificationChannel`
has no comparable per-implementation blocking risk. If one of them later grows the same profile — many
implementations, each pulling in dependencies or risk the others do not share — that is its own decision to
make then, on that registry's own facts, not an automatic consequence of this one.

## Rejected alternatives

**Keep the existing single `capture:infrastructure` module and rely on file-level discipline.** This is the
status quo `§20` already describes ("a class implementing `JobSource`... one line in existing files") and
is the cheapest option by a wide margin. Rejected because the decision here is not about whether the status
quo compiles safely — it already does — but about whether "cheapest to add" should keep winning as the
source count grows past the four or five in view today. The user's call: it should not.

**Wait until a second real dependency conflict actually appears, per the "deferred, not rejected" pattern
ADR-010 uses for Metro/kotlin-inject.** Considered as the more conservative move — no module split until a
concrete case demonstrates the cost. Rejected here, not because the pattern is wrong in general, but
because it was the position this repository argued for and the user overruled it directly: the decision is
made now, deliberately, ahead of the pain that would otherwise justify it later.

**Generalize immediately to every Strategy/Registry point in the catalogue, keeping one granularity rule
system-wide.** Considered for consistency's own sake. Rejected: `capture`'s argument rests on a growth and
per-source-divergence profile that has not been shown to hold for `NotificationChannel`, `LlmProvider`, or
`CompensationModel` yet, and applying a costly restructuring to registries that do not have that profile
would be paying `capture`'s bill on their behalf without evidence.

## Open point

`modules.yaml`'s `layers` map and the `validateModuleGraph` plugin (§15.2) currently model one module per
layer per capability — `infrastructure` is a single entry, not a family. Recognising seven `infrastructure`
leaves under `capture` as all satisfying the same `infrastructure` dependency rule is a real change to that
plugin, not yet designed and not resolved by this record. It is deferred to whichever slice actually starts
building `capture` (Milestone 2), because no code exists yet to validate against and a graph-validator
design done now would be guessing ahead of the thing it validates.
