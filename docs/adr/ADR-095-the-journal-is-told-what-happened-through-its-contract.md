# ADR-095. The journal is told what happened, through its contract

## Status

Accepted. Chosen by the owner on 2026-10-06 while planning slice 6 (`https://claude.ai/artifact/RQeqScGGQz8jxdtDxhJinr`;
diagrams in `docs/backend/05-authentication-slice-6-journal.md`), each fork the recommended option: the entries about the
second factor name the device of the request (1), "first sign-in from a device" is read from the journal's own history
(2), one entry is written when a guess is stopped (3), the Activity page is its own page (4).

Refines ADR-083 (a journal the user can read, and notifications behind a port): the journal is not a listener.

## Context

ADR-083 made the journal its own small module that "listens to events published by `identity`, `authentication` and
`sessions`". Written down as a design it needed an event class in the contract of each of the three, a new `contract`
layer in `sessions`, a bus handed to all of them, and a journal that reads three contracts. The journal would have known
more about the other modules than any of them knows about each other, for the sake of a list of eight lines.

## Decision

**`journal` has a contract, and the other modules call it.** `SecurityJournal` has one method for each thing that can
happen (a sign-in, TOTP turned on or off, recovery codes issued again, a recovery code spent, signing out elsewhere, a
guess stopped). The use cases that cause it call it inside their own transaction. The journal reads only `identity`'s
`AccountId`; `sessions` and `authentication` read the journal, as they read `identity`. Records are still written in the
transaction of the act: an act that rolls back leaves no record, and a record that fails takes the act with it (ADR-052).
The module is called `journal`; `security-journal` is not a package name.

**The device comes with the request.** `Caller.Signed` and `Caller.Confirmed` carry an opaque reference to the session
(`Requester.session()`), the use case passes it on, and the journal finds the device in its own entry for the sign-in
that began that session. It copies the device into the new entry, so a later rename or sign-out changes nothing in the
journal. `sessions` is never asked. A session older than the journal has no device in an entry, which is shown as such.

**"First sign-in from a device" is a hint read from the journal.** A sign-in is marked first when the account has no
earlier sign-in in the journal from the same browser, system and class of device (phone or not). The name of the device
does not count. It is not a protection: someone on the same browser and system is not flagged.

**One entry when a guess is stopped.** The attempt that closes after its wrong codes writes one entry, in the
transaction that commits the last wrong answer. Each wrong code is not written, so guessing cannot fill the journal.

**What is notable is the domain's to say.** `Entry.isNotable()`: TOTP turned off, a recovery code spent, signing out
elsewhere, a guess stopped, and a first sign-in from a device. `SecurityNotifier.notify` is told every entry, right after
it is written; the log adapter writes the kind, the account, the device and the time, and nothing else.

**A rule for the port.** The notifier runs inside the transaction (there is no "after commit" hook, and `platform:outbox`
is empty). An adapter must therefore do no irreversible input/output there. A line in the log can remain for an act that
rolls back, which a log adapter can bear; an email adapter puts the message in the queue and sends it from there.

**Deletion.** Nothing publishes an account deletion yet. The deletion that arrives removes the person's entries in its own
transaction, as it removes their sessions.

## Alternatives considered

**The journal listens to events** (ADR-083 as first read). Rejected for the cost above: the same facts, three event
classes, a new layer, a bus, and a journal that depends on every module it records.

**Each module keeps its own log and the page merges them.** Rejected: paging and order across three tables on every
read, and a fourth place for each new kind of entry.

**No device on the entries about the second factor.** Rejected: it drops the one thing that tells the owner "this was
not me" where it matters most.

**A device cookie for "new device".** Not now: a long-lived cookie with its own life, wiping and consent is a feature of
its own. It stays the way out when email arrives and the notifier needs a firmer answer than a hint.

**An entry for every wrong code.** Rejected: one person mistyping six digits would show as a security event, and a guess
could add ten rows per pass.

## Consequences

Two modules gain a dependency on `journal`, and `Caller` gains a field; `modules.yaml` lists both. The journal's
contract grows by one method for each new kind of entry, which is the place a new kind has to be thought about.
