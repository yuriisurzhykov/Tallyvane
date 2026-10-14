# ADR-099. The admin screens start a version from a copy, act in place, and let the server judge the bounds

## Status

Accepted. Chosen by the owner on 2026-10-14 while planning slice 7c (`https://claude.ai/artifact/MWwuVM9DU5TuyqzUhKuM2e`;
diagrams in `docs/backend/07-authentication-slice-7-admin.md`, section 8) and confirmed by building it: the look follows the
board "S9 · admin policies" of the design canvas "Tallyvane auth screens". Builds on ADR-098 (versions through an API of the
administrators' site), ADR-097 (the administrators' door), ADR-100 (the sign-in flow is a package) and ADR-091 (no modal
windows).

## Context

Slice 7b gave an administrator an API to keep versions of a sign-in policy and of session lifetimes and to put one in force.
Slice 7c is the two screens that use it, behind a gate on the administrators' site. The screens decide what the person sees and
in what order; the server decides what is allowed (ADR-098). The design canvas shows the activation of a version as a modal
window, which the product does not have.

## Decision

**A new version always starts from a copy of an existing one.** The form of a policy is a set of steps (the factors a step
accepts, whom it applies to) and three limits; the form of lifetimes is three lengths. Every edit gives a new form object, so
a screen holding one can tell that something changed by looking. There is no empty form and no free-text editing.

**Creation is a drawer, activation and rollback are a panel under the row.** The panel loads the consequences of the version
(`GET /policy-previews`, `GET /lifetime-previews`) before it offers the button, asks for a reason, and puts the version in force.
A rollback is the same panel with another title. When the server asks for a recent proof of who the person is, the confirmation
of `auth-kit` rises over the page by itself and the request is repeated; a person who declines it goes back to rest without an
error.

**The screens do not know the bounds.** The server answers `422` with every violation at once; the screen words each one next to
the field it belongs to, or in a list above the buttons, and keeps what the person typed. A fault the screen has no words for is
still shown, with the server's field and code, so none is lost when the server learns a new rule.

**One list, two entities.** The list of versions asks every version the same few questions (number, in force, who made it, who put
it in force) and is handed the way to summarise a version, the activation to run and the words for its consequences. The policy
entity and the lifetime entity do not know each other; what they share (a length of time, a rejection, who made a version) is in
the app's own `shared`.

**The gate is the app's own.** `AdminGate` is built from `Viewers`, `ViewerProvider` and `ReauthProvider` of `auth-kit`, in front of
every page of the app, the CMS placeholders included. A person without the right is turned back by the server right after Google
(ADR-097), so the app has no "no access" state; an administrator without an authenticator reaches `restricted`, whose text the
app supplies and which points at the console, where the authenticator is set up.

## Consequences

- The menu has five items instead of three; `/` still redirects to `/pages`, which is also where a finished sign-in lands.
- The list shows "You", a short form of the account id, or "Migration" for an author, because the contract carries ids and no
  names. Names are a later change of the contract, not of the screens.
- `ProblemError` of `frontend-shared` says its faults and its detail, so any screen can word a `422` instead of showing a
  generic failure.
- The administrators' site calls the API through a second internal name of nginx (`nginx-admin`), which sets the admin `Host`;
  the backend chooses the door from `Host` alone (ADR-097) and Node's `fetch` does not let a caller set it.
- The administrators' site has no sign-out button yet; it is not part of this slice.

## Alternatives considered

**A modal window for the activation, as the canvas draws it.** The shortest path from the design, and a second pattern for
confirming something next to the one the product already has. Rejected: ADR-091 says editing is in place and creation is in a
drawer, and the same content fits a panel under the row.

**An empty form, or a JSON editor, for a new version.** Allows any policy the server accepts and nothing else is needed to
start. Rejected: a policy is easiest to get right by changing a working one, and a free-text editor would put the bounds back
in the person's head.

**Checking the bounds on the screen as well.** Gives an earlier message and a second copy of every limit that the code sets.
Rejected by the plan: the two copies drift, and the server must refuse anyway.

**One generic "version" entity for both screens.** Fewer files, and an object that has to hold the steps of a policy and the
lengths of a session at once. Rejected: the two share a few questions, which an interface of the list states, not a body.

**Previewing a draft before saving.** Rejected in ADR-098; the screen follows: a kept version costs nothing and is previewed by
the same call for a new version and a rollback.
