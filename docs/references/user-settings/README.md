# User settings framework — interactive references

These prototypes record the interface explored with the user. They are design references for future agents, not production implementations or a completed framework specification.

## Files

- [Desktop prototype](desktop.html): user settings and admin console with shared visual structure.
- [Mobile prototype](mobile-drawer.html): phone-width preview with a left navigation drawer. **This is the accepted mobile navigation direction.** The older responsive list navigation in `desktop.html` is superseded by this version.

The files are self-contained HTML fragments with inline styles and scripts. They can be opened in a browser; optional Codex host helpers provide state persistence, icons, and design controls when available. Icons may be absent outside that host. The mobile preview constrains its container to 390px and uses container queries.

## Agreed behavior

- Share the interface foundation between the main application and admin console; each application composes its own sections.
- Keep domain data, API operations, authorization, and multi-step flows inside independent modules.
- Apply toggles immediately, with saving feedback. Production failures must restore the confirmed value and show an error.
- Save user-entered fields explicitly. Group related fields into a form with Save and Cancel actions; retain entered values on failure.
- On leaving a dirty form, offer Stay or Leave without saving. Browser unload uses the native browser warning.
- Complex modules can report unfinished progress to the shared navigation guard without exposing their internal steps.
- Desktop uses a persistent sidebar. Mobile uses a left drawer opened by the Sections button; selection closes it after any required dirty-state confirmation. Outside click, Close, and Escape dismiss it.
- Build production React UI from public `frontend-shared` components. These standalone HTML references do not establish a new production component library.

## Demonstrated interactions and limitations

- Switch between personal and admin contexts; edit and save profile/organization fields; toggle notifications; navigate away from a dirty form.
- Security includes a simulated authenticator setup using demo code `123456`. No real secrets, authentication, or network writes are involved.
- Password and admin policy actions are explanatory placeholders, not implemented workflows or approved security policies.
- Saving uses timers and does not exercise server errors, concurrent updates, or real persistence. Profile values are in-memory; optional host state retains selected navigation and toggle values.
- Section navigation does not implement real URLs/history. The multi-step flow remains a dialog on mobile.
- JavaScript syntax was checked with `node --check`. Browser rendering, accessibility, and end-to-end behavior have not been verified.

Use these references alongside repository architecture rules. A production contract, implementation plan, and integration verification remain separate work.
