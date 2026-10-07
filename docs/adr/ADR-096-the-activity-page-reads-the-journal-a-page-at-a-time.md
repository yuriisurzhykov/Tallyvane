# ADR-096. The Activity page reads the journal a page at a time

## Status

Accepted. Chosen by the owner on 2026-10-07 while planning slice 6b (`https://claude.ai/artifact/LDjnbgcoxwCdTRRAsDMsgH`;
diagrams in `docs/backend/06-authentication-slice-6b-activity-page.md`), each fork the recommended option: more entries
come by a "Show more" button (1), and every entry shows its date and time (2). The page being its own, at
`/settings/activity`, was decided with slice 6 (ADR-095, fork 4).

## Context

ADR-095 gave the backend `GET /api/v1/security-activity`: the signed-in person's entries, newest first, thirty at a time,
with a cursor. The console needs a page for it. It is the second screen that reads a list on the client (ADR-091), and it
meets two things the Devices page did not: a list that is read in parts, and a device that is described in a second place.

## Decision

**The page is `/settings/activity`, read on the client.** The times depend on the person's zone, so they cannot be in
server-rendered HTML. The list is read when the page opens and on "Try again"; nothing polls.

**More entries come when the person asks.** A "Show more" button asks for the page after the one on the screen, passing
the server's `next` back as `before`. The list holds the cursor (`ActivityLog`); the client never builds one. A failed
"Show more" leaves the list as it is.

**Every entry shows its date and time**, in the person's zone, in one flat list.

**What is notable is the server's to say.** The client gives a badge to the entries ADR-095 calls notable and does not
decide that again; a stopped guess is painted `danger`, the other notable entries `attention`.

**A device is named in one place.** `DeviceLabel` in `entities/device` makes "Chrome on Windows · mobile"; `Device` and
`SecurityEntry` both use it, the entry through the `@x` interface between entities (ARCHITECTURE §12.4).

**The API client learns query parameters.** `Api.get` takes a typed `query` option. The path stays the key the
specification spells, and the names and values of the query come from the operation, so a misspelt parameter is a compile
error, as a path without its `params` already is.

## Alternatives considered

**Infinite scroll.** Rejected: an observer, a second path for the keyboard and screen readers, and a failure state in the
middle of scrolling, for a page whose first entries are the ones read.

**Entries grouped by day.** Rejected: it needs grouping by the person's local day, headings in the list and tests on the
day boundary, and the exact time on each entry answers "was that me?" without it.

**Copying the device words into the entry.** Rejected: one word would be a change in two places.

**A path with the query in it** (`"/security-activity?before=…"`). Rejected: the path is checked against the keys of the
specification, so it would need a cast, and a misspelt parameter would pass unnoticed.
