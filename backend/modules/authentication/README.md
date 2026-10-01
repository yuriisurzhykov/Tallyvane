# authentication

How a person proved who they are (ADR-076). Factors, sign-in attempts and the
policy that decides when an attempt is enough. It does not know who the person
is beyond an account id (`identity`), and it never issues access: a complete
attempt is redeemed for a session by `sessions`.

Three layers exist: `domain`, `application` (the ports) and `infrastructure`
(Postgres, schema `authentication`, ARCHITECTURE.md 8.3.1). Storing a sign-in
attempt and the numbered versions of a policy is slice 2; the use cases that
start an attempt and redeem it arrive with slice 3.

## domain

Pure Kotlin, no clock, no database. Every instant arrives as an argument, so
each rule is checked by a test that names the exact second it is about.

| Type | What it is |
|------|------------|
| `FactorKind` | Google, TOTP, recovery code; whether each needs setting up first |
| `Enrollment` | The factors one account has set up and confirmed; `Unknown` before we know whose it is |
| `VerifiedFactor` | The one fact a method reports: this kind, verified at this instant |
| `Step` | One thing a sign-in must show: any of these kinds, always or only if enrolled |
| `Purpose` | Registration, login, admin login, step-up; and the floor a policy for it may not go below |
| `PolicyDraft` | A policy as an administrator proposed it; `check()` is the only way to a `SignInPolicy` |
| `DraftCheck` | What the check found: the policy, or every `Violation` at once |
| `Violation` | One broken bound, with the value given and the range allowed |
| `SignInPolicy` | The steps of one purpose, plus attempt lifetime, failure limit, first delay |
| `PolicyVersion` | One numbered, checked policy of a purpose; the unit that is stored, activated and rolled back |
| `Attempt` | What happened so far in one sign-in, for one purpose; records, never decides |
| `Progress` | Where the attempt stands: complete, restricted, awaiting, paused, exhausted, expired; read only through `Progress.Report` |

### Encapsulation: no field leaves an object

2026-10-01. Every field is `private`. An object answers questions about
itself (`Enrollment.includes`, `FactorKind.isAvailableTo`) or does the work
itself; it never hands out its lists. Two consequences shape the code:

- **Tell, don't ask.** The policy decides *which* outcome applies, but never
  reads an attempt's factors to build it. It tells the next step to request
  itself from the attempt (`Step.requestFrom`), and the attempt, which alone
  holds the facts, states the outcome (`Attempt.complete`,
  `Attempt.restrictedTo`, `Attempt.awaiting`).
- **`Progress` is read through `Report`.** Each case keeps its contents
  private and calls the one `Report` method that matches it, with exactly
  what it carries. A reader cannot forget a case (it would not compile) and
  cannot ask a paused attempt for an authentication time it does not have.

`internal` is used only for one type in this module asking another a
question; it never crosses a Gradle module. The one exception in spirit is
`VerifiedFactor`, whose two values are `internal` so `Attempt` can read them:
it is a bare fact with no invariant to protect.

Constructors of the `Progress` cases are `internal`, so nothing outside this
module can produce a `Complete` that no policy granted. `Exhausted` and
`Expired` are classes, not `object`s, because the architecture rules keep
functions off objects; they define equality so every instance is the same
value.

### Storing an aggregate that has no getters

2026-10-01, ADR-085. `Attempt`, `SignInPolicy` and `PolicyVersion` keep every
field private and still have to be stored, in another Gradle module. Each
publishes a nested `Record` interface and a `writeTo(record)` that says all of
its facts in a fixed order; the adapter implements `Record` and writes rows.
`Attempt.restore` and `PolicyVersion.restore` take the replay the adapter makes
from the rows, hand it the aggregate's own `Record`, and re-check the history
on the way in, so a hand-edited row cannot produce a state the domain refuses.
`Progress` is computed from an attempt and a policy and is never stored.

`PolicyVersion.restore` checks the policy against the bounds in the code
*today*. Narrow a bound in a release and a stored version outside it stops
loading, with a message that says to activate another version, instead of
being judged under numbers the code no longer allows.

### Why `Attempt` is not a `data class`

A `data class` gets a public `copy()`, and `attempt.copy(failures = emptyList())`
would wipe the record that limits guessing. `Attempt` has a private primary
constructor; the only ways to change one are `withVerified` and
`withFailure`, and both only add.

### Why a policy is "all of these steps, each by any of those kinds"

2026-10-01. The first sketch was a tree of `AllOf` / `AnyOf` / `Factor` nodes,
which can express anything. It was dropped before any code: every policy
ADR-078 starts with is a flat list of alternatives, and a tree makes two
questions harder than they need to be. The sign-in screen has to show *the
next thing to do*, which in a list is the first unsatisfied step and in a tree
is a search. And an administrator editing a policy reads a list, not a nested
expression. If a policy ever needs nesting, the list becomes one node type of
a tree without changing any caller.

### Why the attempt does not store its policy

Tightening applies immediately (ADR-078), including to someone half-way
through a sign-in. So the attempt only records facts, and whichever policy
version is active is asked about them on every step. The test "the same
attempt is judged by whichever policy is active when asked" pins this.

### Why "restricted" is its own outcome

A policy may demand a factor the account has not set up: an administrator
without TOTP under `admin_login`. Treating the step as merely unsatisfied would
lock them out for good, since setting TOTP up requires being signed in.
Treating it as satisfied would let them in unrestricted. `Progress.Restricted`
is the third answer: signed in only to set the factor up. `FactorKind.isAvailableTo`
is what lets the policy tell "not verified yet" from "cannot be verified yet".

### The code sets bounds, the policy sets values

2026-10-01. An administrator chooses the numbers; the code decides which
numbers may be chosen (ADR-078). `SignInPolicy` has an `internal`
constructor, and the only caller is `PolicyDraft.check()`, so a policy
outside the bounds does not get refused somewhere later: it cannot exist.

| Value | Allowed | Initial |
|-------|---------|---------|
| Attempt lifetime | 1 to 15 minutes | 5 minutes |
| Wrong answers before the attempt ends | 3 to 10 | 5 |
| First pause after a wrong answer | 1 to 10 seconds | 1 second |

A refusal lists every broken bound, not the first one, so a form is fixed in
one pass. The violations are not kernel `Failure`s: they are what the domain
found, and the use case that saves a policy version will wrap them in its own
outcome, which is where `Failure` and its `Problems` mapping belong.

Each `Purpose` sets a floor no policy for it may go below:

| Purpose | Floor |
|---------|-------|
| `Registration` | none beyond identifying the account |
| `Login`, `StepUp` | a second factor from every account that set one up |
| `AdminLogin` | a second factor from every account |

The middle row is ADR-078's "a user can only raise their own bar" made into a
check: an administrator cannot save a sign-in or step-up policy that lets
someone with TOTP through on Google alone. "TOTP or Google" does not count as
a second factor, since Google alone would pass it. Added 2026-10-01 after a
review found that step-up had no floor at all.

The policy `check()` returns owns copies of the steps and their sets, so a
form model reused after saving cannot reach into a policy that passed.

### Confirming a dangerous action asks as much as signing in

2026-10-01, the owner's decision. `step_up` has the same steps as `login`:
Google, and the code too if TOTP is on. An earlier draft of ADR-078 said
"TOTP if enabled, otherwise Google", which would have let someone who took
over the Google account switch TOTP off with Google alone, or skip Google and
confirm with the code alone. Two steps cover it with the model as it is.

### What is deliberately not here yet

- **Who wrote a policy version, and why.** A version stores its number,
  purpose, steps and limits, and is activated by a row in
  `policy_activations`. The author (an account id from `identity`) and the
  comment arrive with the administrator use cases of slice 7.
- **An unguessable attempt id.** `Attempts` stores whatever id it is given. The
  id is a UUIDv7 today, which is not a secret; if the attempt id becomes the
  bearer of a sign-in, slice 3 turns it into a random value and stores only
  its hash.
- **Session lifetimes** (idle 15 minutes to 30 days, absolute at most 90
  days) are bounds of the same kind and arrive with session management.
  Step-up freshness is a comparison on the session's authentication time and
  belongs to `sessions`.

### Tests that can fail

2026-10-01, policy core. Ten deliberate bugs were planted one at a time and the suite was
run against each: expiry one second late, a sixth wrong answer allowed, linear
instead of doubling delay, a pause one second too long, the user's own TOTP
ignored, the administrator's missing TOTP waved through, authentication time
taken from the first factor instead of the last, the "policy must identify the
account" check weakened, a paused attempt reported as awaiting, and a
restricted one that forgets what to set up. Every one turned the suite red.

2026-10-01, drafts and bounds. Ten more: each bound loosened by one unit at
one end, the lifetime check skipped, an empty step's position off by one,
the identification check dropped, the admin floor removed, Google counted as
a second factor, an optional step counted as a mandatory one, and only the
first group of violations reported. Every one turned the suite red.

2026-10-01, storage. Twelve more against the Postgres adapters: the row lock
on an attempt taken out; the advisory lock on `add` taken out; the check that
the kept failures are a prefix of the new ones dropped, and the same for
verified factors; the start of an attempt not compared; instants not cut to the
microsecond; the earliest activation winning instead of the latest; each of the
two limits stored as whole seconds; steps stored in reverse order; the trigger
that refuses to change an activation removed. Every one turned the suite red,
but not at first: two survived (a stale save that would drop a verified factor,
and limits with milliseconds, which a test comparing the stored version with the
one `add` had just returned could not see, since both passed through the same
bug). Each got a case of its own.

## application

The ports only, so far. `Attempts` keeps attempts and answers `Saved` or
`Superseded` (an attempt only grows, so a save that does not contain what is
already kept loses to it, which is what stops two parallel guesses from both
counting as the first). `PolicyVersions` adds a checked policy as the next
version of its purpose, activates a version, and finds the one in force.

## infrastructure

Postgres adapters over six tables (ARCHITECTURE.md 8.3.1). The history is
append-only and the database says so: triggers refuse every `UPDATE` and
`DELETE` on versions, steps and activations. The initial policies of ADR-078
are rows of the second migration, read back through the domain by a test, so
a fresh database can sign someone in before any administrator has acted.

## SOLID

**Single responsibility.** `Attempt` records and states outcomes from its
record, `SignInPolicy` judges which outcome applies, `Step` answers questions
about one step. None of them reads the clock or a database.

**Open/closed.** A new factor is a new `FactorKind` entry and a new method in
`application`; no evaluation code changes. A new purpose is a new `Purpose`
entry with its floor; a new bound is a new `Violation` case, and every
reader is told by the compiler to handle it.

**Liskov.** Every `Progress` case honours `reportTo` the same way: it calls
exactly one `Report` method, the one named after it. `ProgressSpec` pins that.

**Interface segregation.** `Progress.Report` is one interface because every
reader of a sign-in must handle every case; splitting it would let a reader
silently ignore one.

**Dependency inversion.** `application` owns the ports (`Attempts`,
`PolicyVersions`); `infrastructure` implements them with Postgres and the
test source set implements them with fakes. One conformance suite per port runs
against both, so a fake cannot drift from the real thing.
