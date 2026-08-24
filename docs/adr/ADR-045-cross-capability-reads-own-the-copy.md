# ADR-045. A screen that needs a neighbour's data keeps its own copy, fed by events

## Decision

A capability may not read another capability's tables. ADR-020 and §4.6 already
say that, and `own-schema-only` and `no-cross-schema-join` enforce it in Kotlin.
What was missing is the sanctioned alternative, which made the ban read as a dead
end. There are three ways to build a screen whose data spans capabilities, and
this record fixes which one is the default.

**Ask the neighbour** — the module reads its own page of rows, collects the ids,
and calls the neighbour's `contract` for the rest, joining the results in Kotlin.
This is correct and stays correct while a page is all that is ever fetched. It
collapses the moment a screen sorts or filters by the neighbour's field: to order
by company name you must fetch *every* row, ask for *every* name, sort in memory
and then take twenty. That is the failure mode, and it arrives with cursor
pagination (§11.1), not with row count.

**Keep a copy — the default.** The consumer owns a table in its own schema,
shaped for its screen, holding copies of the fields it needs. The owner announces
changes as events (§4.5); the consumer subscribes and updates its copy. The
screen becomes one query against one schema, so ordering and `LIMIT` go to
Postgres and the cost stops depending on how much the neighbour holds. The copy
lags reality by however long delivery takes, and the subscriber is code somebody
maintains. Both are accepted.

**Let the database join — rejected as the default.** A `create view` in the
consumer's schema joins the owner's tables live. Nothing is copied and nothing
lags, which is genuinely attractive. It is rejected because the SQL then names
the owner's *physical columns*: rename `jobs.companies.name` and the consumer
breaks, having never touched the owner's contract. Worse, the coupling is
invisible to every gate this repository has, because the join lives in a `.sql`
file and Konsist reads Kotlin.

The read side is owned by the consumer in all three cases. A read model exists
because one screen asked for that shape, which makes it a product decision, so
it cannot live in `platform/*` — `platform-knows-no-business` would be right to
reject it — and it must not accumulate in `analytics`, which has a capability of
its own and would become a shared database wearing a module's name.

## What the machine now checks

`MigrationSchemaSpec` reads every `.sql` under `platform/` and `modules/` and
fails when a migration names a schema other than its own. A foreign key is
exempt, because §4.6 allows exactly that and nothing else: `references
identity.users (id)` passes in the same file where `join jobs.companies` fails.

The prescribing half is not machine-checkable and is not claimed to be. Nothing
can prove that a given table is a copy, or that the subscriber keeping it current
exists and is correct. The gate closes the door on the rejected option; the
default rests on review.

A first draft of the check matched every `x.y` in the file and reported table
aliases as schemas — `a.id` in `from applications.applications a`. The pattern is
anchored to the positions where a schema may actually stand.

## Rejected alternatives

**A separate `reporting` schema.** The first shape of this decision put
cross-capability views in a schema of their own, which immediately raised two
questions with no good answer: who owns migrations for a schema belonging to no
module, and how the schema rules admit a module reading it. Both dissolve once
the read model lives in the consumer's own schema — no new schema, no rule
change, and migration ownership follows §4.6 unchanged.

**Relaxing `own-schema-only` to permit a read schema.** Considered together with
the above and unnecessary for the same reason.

**Deciding later, at the first real list.** Cheap now on an empty tree,
expensive once three capabilities have each solved it differently. §21 puts the
gates in Milestone 0 for this reason: "правила, введённые позже, приходится
внедрять против уже написанного кода."

## Consequences

A capability that publishes data others display must publish events describing
changes to it, not only the events its own domain finds interesting. That
obligation belongs in each module's specification as it is written.

Sorting and filtering are limited to the module's own columns until a copy
exists. A screen that wants to sort by a neighbour's field is asking for a copy,
and that is the signal to build one rather than a reason to reach across.
