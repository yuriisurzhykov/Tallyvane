# ADR-100. The sign-in flow is a package; each app gives it its words, its frame and its landing page

## Status

Accepted. Chosen by the owner on 2026-10-14 while planning slice 7-0 (`https://claude.ai/artifact/J6Pfv3xm9vksZrxWTdexoV`;
diagrams in `docs/frontend/03-auth-kit.md`): the package has no words of its own (fork 1, A), the gate and the server-side
API stay in each app (fork 3, B), and the administrators' site refuses a person without the right before it asks for a code
(fork 2, recorded in ADR-097). Carries out fork 4 of slice 7 (`docs/backend/07-authentication-slice-7-admin.md`).

## Context

The sign-in lived inside `frontend-app`: about 1700 lines in ten slices, from the viewer and the state of an attempt to the
screens for signing in, entering a code and confirming a dangerous act. Slice 7c puts a second app, `frontend-admin`, behind
the same sign-in on another host. Copying the flow would make every fix to the second step, the pause after a wrong code, the
pop-up of the confirmation or the recovery-code notice a fix to make twice, in code that guards the one thing the
administrators' site must not get wrong.

What a sign-in asks for is already the server's decision per purpose (ADR-078): `login` and `admin_login` have their own
policies. The screens only follow where the attempt stands (ADR-094), so nothing in them says which app they are in.

## Decision

**The flow lives in `packages/auth-kit`**, with the FSD layers it was made of (`entities`, `features`, `views`) and a small
`shared` segment of its own. Apps reach it through the entry points of its `exports` map, as they reach `content-kit`.

**Three things arrive as parameters, and nothing else differs between apps:**

- the words, as one typed object `AuthTexts`; a key left out is a compile error in the app, and the package ships no words;
- the frame, a component each screen is drawn in (`SignInFrame`); the app keeps its mark, its card and its links;
- the landing page of a finished sign-in, and optionally the page where an authenticator is set up again after a recovery
  code (the button is not shown without it).

**The gate and the server-side API are not in the package.** Each app writes its own gate from `Viewers`, `ViewerProvider`,
`ReauthProvider` and the name of the return-path header, which the package exports together with the proxy helper that sets it.

**Behaviour of the console does not change.** Its words come from its own dictionary, unchanged; its tests moved with the
code and run the same number of cases.

## Alternatives considered

**Copy the flow into `frontend-admin`.** Fastest for 7c and rejected in fork 4 of slice 7: a bug in the second step is fixed
twice, and the two copies drift.

**A package with English words and overrides in the app.** One place for a change of wording and a shorter admin
dictionary, but eight namespaces move out of the console's dictionary into a package that then owns product words. Not
rejected for good: `defaultAuthTexts` can be added later without touching a component. Declined for this slice to keep the
move behaviour-for-behaviour.

**The gate and the server-side API in the package.** Writes a gate once. Declined by the owner: the package stays the parts,
and a gate package of its own can be cut later if the duplication of two short files hurts.

**A "no access" state in the screens** for a person who passed Google and the code and was then refused a session. Declined
by the owner as the wrong place: the person must not get that far (ADR-097), so the screens have nothing to show for it.

**Navigation passed in as callbacks, so the package would not depend on Next.** The screens are Next pages that redirect,
read the router and run in a proxy; the indirection would cost more than it frees. `next` is a peer dependency.
