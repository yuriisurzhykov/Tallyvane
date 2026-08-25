# ADR-049. Exposed, and only its DSL, is the data access layer

## Decision

Adapters in `*:infrastructure` reach PostgreSQL through Exposed's DSL: a `Table`
object declaring columns, and an explicit function mapping a result row to the
module's own immutable value. Exposed's DAO — `Entity`, `EntityClass`, property
delegates — is not on the classpath.

Exposed was already the implied choice and had never been decided. §4.6
illustrates schema ownership with `internal object JobsTable : Table("jobs.jobs")`,
which is Exposed's vocabulary; `org.jetbrains.exposed` is one of the framework
import prefixes Konsist refuses in `domain` and `contract`; and
`backend/README.md` names Exposed among the libraries "not on any classpath yet".
An implication enforced by a linter is not a decision anybody reviewed, hence this
record.

## Why not the DAO

The DAO does not remove the hand-written mapping — it adds a class on top of one.
An `IdTable` is still declared; the `Entity` sits above it with `var` properties
whose delegates issue an `UPDATE` on assignment.

That entity cannot leave `infrastructure`, for three independent reasons. It holds
`var`, which `domain-no-var` and the immutability required of `contract` both
reject. Its file imports `org.jetbrains.exposed`, which fails Konsist in either
layer. And it is bound to the transaction that loaded it, so touching a lazy field
after the transaction closes throws — handing one to a use case plants a fault that
surfaces later, not at the boundary.

So the DAO's payoff exists only where entities *are* the model carried through the
application. That is ActiveRecord, and it is the shape ADR-017 and §4.3 exclude:
`domain` knows nothing but `platform:kernel`, not even its own `application`.

Three conveniences are genuinely given up: lazy traversal of relations, the
identity cache inside a transaction, and the CRUD helpers on `EntityClass`. The
first is the smallest loss of the three, because traversing a relation into another
capability's tables is the cross-schema join ADR-020 forbids anyway, and inside one
module the table count is low. The other two save lines in an adapter, not in a
domain.

There is also a scaling argument, and it runs opposite to intuition. Lazy traversal
is the standard generator of the N+1 problem; the cure is explicit eager loading,
which is manual query control arrived at by a longer road. And the DAO cannot
express what optimisation actually needs — common table expressions, window
functions, `INSERT … ON CONFLICT` with a predicate. The DSL can. If the criterion
is holding up at the volumes §1.5 now requires, the DSL is the more capable choice,
not the more austere one.

## What the DSL costs, and what covers it

Each table needs a mapping function of roughly five lines, and nothing in the
compiler checks that a declared column exists in the database. The second is the
real exposure, and it is why the schema-drift gate exists: migrations are applied
to a throwaway database and Exposed is asked whether it would change anything, so a
`Table` that has drifted from its migration fails the build rather than a query.

## Rejected alternatives

**jOOQ.** Generates typed accessors from a live schema, which inverts the order
this project works in: the schema is authored as SQL in §8 and applied by Flyway,
and code generation would make the build depend on a database being reachable to
compile. The same inversion §11.7 rejects for the HTTP contract.

**Plain JDBC over `java.sql`.** No typed column access and result mapping written
by hand at every call site. In practice a repository grows its own thin query
helper, which is the utility layer §5 forbids and which nobody reviews as an API.

**Hibernate or another JPA provider.** Entity managers, lazy proxies and a
detached-object lifecycle, all of which have to be kept out of the domain — the
DAO objection with more machinery and an annotation processor.
