# ADR-091. The devices screen saves a name once and asks in place

## Status

Accepted. Chosen by the owner on 2026-10-03 while planning the second PR of slice 4
(`https://claude.ai/artifact/9PkfXHgck78W7GeJdMcDp9`), each time the recommended option: the screen is
`/settings/devices` reached from the account menu (fork 1); the list is read on the client (2); a name is saved
on commit, not on a timer (3); "sign out everywhere else" asks in place (4); `frontend-app` gets a test runner (5).

## Context

ADR-090 gave the backend its four calls (`GET /devices`, `DELETE /device/{id}`, `PUT /device-names/{id}`,
`DELETE /other-devices`). The screen is the first in the console that reads data, and it meets two rules written
for other cases: the console saves a field 400 ms after the last keystroke (ARCHITECTURE §12.9, `InlineEdit`),
and the product has no modal windows (ADR-089 made `Dialog` the one exception, for a session that cannot go on).

## Decision

**The address.** `/settings/devices`, linked from the account menu only. The settings page that will hold it
does not exist, and the console's side navigation is for the job search, not for the account.

**The list is read on the client.** The times ("3 hours ago") depend on the visitor's clock and zone, so they
cannot be in server-rendered HTML. `useDevices` reads when the screen opens and after every action: the server's
answer is the list, nothing is edited locally. A `404` on sign-out counts as done.

**A name is saved once, on commit.** It is written into the security journal (ADR-083), and the server refuses an
empty one, so saving after every pause would journal "Wo" and "Work la" and refuse a field the person is
rewriting. `InlineEdit` is used as is, with a delay no timer reaches, so only Enter or leaving the field saves.
A refused name shows the reason under the field and starts the field over with the name the server has.

**Sign-out everywhere else asks once, in place** ("Sign out 2 other devices?"), not in a dialog. Signing out on
one device does not ask: it is undone by signing in again, like signing out from the menu. Neither asks for a
fresh factor yet; slice 5 adds that (ADR-090).

**The client learns `PUT` and path parameters.** `Api` gets `put` and a typed `params` option: the path stays the
key the specification spells (`"/device/{id}"`), the values are escaped into it, and a path with a segment
needs `params` to compile.

**What to call a device is decided in the interface.** The server sends browser, system and mobile as parts;
`Device` composes "Chrome on Windows" in the screen's language, and the name the person gave comes first.

**`frontend-app` gets `vitest`** for plain classes (`Device`, `Devices`), in a node environment. Components are
looked at in a browser, as before.

## Consequences

The first paint of the screen is empty rows, and the list arrives a moment later. A name is lost if the page
closes before the field loses focus. The screen is the first in the console whose request can end the session, so
it is where the re-sign-in dialog of ADR-089 first appears for a reason a person can meet. Not verified against
the real backend or Google: only a stand-in API in Chromium.
