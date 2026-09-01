# ADR-071. A role hierarchy graph replaces the two-flag capability model, reusing Career Graph's relational-edges pattern

## Decision

`identity`'s access model (§7.4) is not two independent flags (`console.use`, `content.manage`) but a graph of roles
that can extend other roles: `identity.roles`, `identity.role_permissions`, `identity.role_hierarchy`
(`child_role_id`/`parent_role_id` edges), `identity.user_roles`. A user's effective permissions are the direct
permissions of their role(s) plus every permission inherited by walking `role_hierarchy` upward — computed by a
recursive CTE, the same technique `career.graph_edges` already uses (ADR-034), not a graph database and not a
role-inheritance library. The published port, `identity.contract.Access`, returns a `Grants` value object rather than
exposing a query-by-ID check method.

## Why

Real access levels are not independent: a subscriber can do everything a plain user can, plus more; a platform
administrator can do everything a billing administrator and a content administrator can, plus whatever is specific to
neither. A flat set of flags expresses this only by manually copying every permission from the smaller role into the
bigger one each time either changes. The two-flag model's own stated principle — "a set of capabilities, not a role
hierarchy" — was true of two capabilities with no relationship between them, and stopped being true the moment a
third capability needed to be a superset of one of the first two.

The mechanism is deliberately not novel. This repository already solved "walk a small, per-user graph of typed edges"
for Career Graph (ADR-034), for the same underlying reasons: the edge count per user is small (roles, not millions of
rows), and a dedicated graph engine would add a second storage technology for a shape PostgreSQL already expresses
with a table and a recursive query. Reusing the pattern means the only new idea here is the data being walked, not the
walking itself.

`Access.grantsOf(userId): Grants` — a value obtained once and then asked a question — replaces a
`PermissionChecker.has(userId, permission): Boolean` shape that was drafted and reconsidered during design. A
static-feeling utility taking a raw id and a raw permission is a service standing in for behaviour an object should
carry itself: the same objection this repository's engineering voice raises elsewhere against "-er" utility classes
that operate on borrowed identifiers instead of being asked a question by something that already holds the answer.

## Rejected alternatives

**A single wildcard/superuser flag for "full access."** Considered so a new permission is automatically available to
whoever should have "everything," without editing every admin role's permission list by hand. Rejected: it is one
special-cased bypass sitting outside the rest of the model's logic, checked differently from every other permission —
a small but real exception to "every permission is checked the same way," where the graph achieves the identical
outcome (`platform_admin` extends every role whose permissions it should inherit) without a special case.

**Keep the flat set, and define "full access" as the manually maintained union of every other role's permissions.**
Rejected on its own terms: it keeps the stated principle literally true at the cost of a manual synchronization step
that has to be remembered every time a new permission is added — exactly the kind of thing that drifts silently, and
the drift stays invisible until someone with "full access" discovers they do not actually have it.

**A general role-inheritance engine — roles as an open extension graph resolved by a library, or a generic
multi-level traversal service.** Rejected as more machinery than the real requirement calls for: a handful of named
roles (`user`, `subscriber`, `content_admin`, `billing_admin`, `platform_admin`, and whatever is added later) is a
small, mostly static set, not an open-ended hierarchy that needs runtime authoring tools — the same argument ADR-010
makes against a dependency-injection container at this scale, applied to authorization instead of wiring.
