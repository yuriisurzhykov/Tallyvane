# Policy-driven sign-in and account recovery

**Status: draft for owner review**

This specification supersedes the sign-in and backup-code decisions in
`2026-09-25-composable-authentication-tokens-and-schemes-design.md`. The existing action-proof
design remains applicable where this document does not replace it.

## Intent

Make the administrator-owned authentication policy the sole source of truth for accepted sign-in
paths. The server must evaluate the schemes saved by an administrator, and the app and admin login
screens must render the choices returned by that evaluation. Clients must not reproduce policy
rules, and the server must not add an implicit rule such as "any enrolled method forces MFA."

Keep recovery codes as a user-managed account-recovery capability. A recovery code is neither a
primary sign-in method nor a second factor and must never appear in an ordinary sign-in scheme,
pending MFA challenge, or action-proof scheme.

## Responsibility boundaries

### Authentication policy

The versioned policy contains enabled schemes for an action. A `SIGN_IN` scheme contains exactly
one primary proof and at most one second-factor proof.

Supported primary proofs are:

- `PASSWORD`
- `GOOGLE`
- `EMAIL_SIGN_IN_CODE`

Supported second-factor proofs are:

- `TOTP`
- `EMAIL_FACTOR_CODE`

The administrator decides which combinations exist and assigns their assurance ranks. For
example, an administrator may enable `PASSWORD`, `GOOGLE`, `PASSWORD + TOTP`,
`PASSWORD + EMAIL_FACTOR_CODE`, and `GOOGLE + TOTP`.

`BACKUP_CODE` is removed from the policy token catalog. It cannot be selected in the admin policy
editor or persisted in a new scheme.

### Sign-in evaluator

The evaluator receives a successfully presented primary proof, the current policy, and the user's
actual credential and factor inventory. It returns either an issued session, a pending
second-factor challenge, or a generic refusal.

The evaluator applies the stored policy literally:

1. Select enabled `SIGN_IN` schemes containing the presented primary proof.
2. Remove schemes whose additional proofs are not enrolled or otherwise available to this user.
3. If no scheme remains, refuse sign-in without revealing whether the account, credential, policy,
   or factor caused the refusal.
4. Select the remaining scheme with the greatest assurance rank as the recommended scheme.
5. If the recommended scheme contains only the already verified primary proof, issue the session.
6. If the recommended scheme contains a second factor, create a pending authentication. Its
   recommended method comes from the recommended scheme. Its available methods contain every
   second-factor method from the remaining compatible sign-in schemes.

For sign-in, assurance rank selects the default path shown to the user. Every method returned in
the pending authentication remains an accepted alternative because its scheme was explicitly
enabled by the administrator. Strongest-only enforcement for sensitive authenticated actions is a
separate action-proof rule and does not remove configured sign-in alternatives.

When the user has no enrolled second factor that completes a configured factor-bearing scheme, a
configured primary-only scheme can issue the session. Enrollment is not invented or required by
the server. After the session begins, a client may show a non-blocking invitation to configure
two-factor authentication.

### Account recovery

Recovery codes stay available in account security settings. Issuing or replacing them remains a
user-controlled, protected account action. Their existence does not change the user's sign-in
inventory and does not cause an MFA challenge.

Using a recovery code starts a dedicated account-recovery operation rather than
`POST /auth/mfa/verify`. The recovery operation identifies the account by its email address and
uses the recovery code as the ownership proof. Access to the mailbox is not required. If the user
does not remember the account email, the current email-identified account model cannot locate the
account; making a recovery code self-identifying is outside this change.

Successful recovery is atomic:

- consume the presented recovery code;
- replace the password with the validated new password;
- revoke all access sessions and refresh tokens;
- delete pending sign-ins;
- remove the old TOTP and email-factor enrollments;
- unlink the old Google credential, because a full recovery must invalidate every previous external
  primary credential as well as the password;
- invalidate all remaining recovery codes from the old set;
- issue a new session for the recovered account.

The recovered user is then invited to configure second factors, relink Google if desired, and issue
a fresh recovery-code set. Google linkage does not restrict whether a user may issue recovery
codes; it is only invalidated when a recovery code is actually used for full account recovery.

## HTTP contracts

### Public sign-in options

Add a public, account-neutral sign-in-options response derived from the current policy and runtime
provider availability. It lists which primary entry points the initial login screen can display.
It contains no user-specific credential or enrollment information.

The app and admin login screens use this response so a disabled primary method cannot remain as a
working-looking client control. Route handlers still enforce policy; hiding a control is not an
authorization boundary.

### Primary sign-in outcome

Password, Google, and any enabled email-sign-in-code handler return the same logical outcomes:

- `issued`: attach access and refresh cookies and return/redirect to the requested safe destination;
- `requires_second_factor`: return a pending identifier, one recommended method, and all methods
  accepted for that pending authentication;
- generic refusal.

The JSON shape for a second-factor outcome is:

```json
{
  "status": "requires_second_factor",
  "pending_id": "uuid",
  "recommended_method": "TOTP",
  "available_methods": ["TOTP", "EMAIL_OTP"]
}
```

`recommended_method` is required and is always a member of the non-empty `available_methods`
array. Only actual second factors appear in either field.

### Pending verification

The existing verification request continues to send:

```json
{
  "pending_id": "uuid",
  "kind": "EMAIL_OTP",
  "code": "123456",
  "challenge_id": "uuid when required"
}
```

The server, not the client, verifies that `kind` belongs to the pending authentication's stored
available methods. A valid proof for either the recommended method or any returned alternative
completes the pending authentication and issues the same normal access/refresh session. An email
factor code can only be requested when `EMAIL_OTP` belongs to that pending authentication.

A pending authentication stores the user, device, policy version, recommended method, complete
available-method set, creation time, and expiry. If the referenced policy version is no longer
acceptable at verification time, the attempt expires and the user starts sign-in again.

### Recovery

Add a dedicated recovery endpoint accepting account email, recovery code, new password, and device
label. It returns an issued session on success. Unknown email, wrong or already consumed code, an
invalid recovery state, and other credential failures use the same generic public error. Password
validation happens before code consumption; the credential replacement and security reset commit
in one use-case transaction.

## User experience

### Initial screen

Before the account is known, show only the primary entry points enabled by public sign-in options.
The current presentation remains: Google when available and the email/password form when password
sign-in is enabled. No user-specific factor choice is shown on this screen.

### Second-factor screen

When the primary sign-in outcome requires another proof:

- open the form for `recommended_method` immediately;
- do not begin with a select box or a row of equally prominent factor buttons;
- show `Sign in another way` only when `available_methods` contains another method;
- reveal the remaining server-provided methods when that control is activated;
- selecting an alternative replaces the active form and submits that method's `kind`;
- requesting an email factor code remains a distinct first action before entering the delivered
  code.

The app and admin implement the same interaction and consume the same wire contract. Domain copy
and orchestration remain in their feature/view layers; they compose existing public
`frontend-shared` controls.

### After sign-in without MFA

When a primary-only scheme issues a session and the user has no second factor, the application may
show a dismissible invitation to configure two-factor authentication. This invitation does not
delay access to the authenticated destination and does not change policy evaluation.

### Recovery-code screen

Recovery is linked from account-access help, not from the MFA method picker. The form explains
that one code performs full account recovery, consumes the old code set, signs out other devices,
and requires a new password. After completion, account security explains that the old factors were
removed and offers setup actions.

## Administration

The admin policy editor continues to edit the same versioned schemes consumed by the server.
Remove recovery codes from all token selectors and labels used for schemes. Validation must match
the server's domain validation and must not impose a client-only rule that the evaluator ignores.

Saving a policy increments its version and keeps optimistic conflict handling and audit recording.
The public sign-in-options response changes immediately after a successful save. New sign-ins use
the new version; pending sign-ins follow the stale-policy behavior described above.

The admin console does not maintain a second interpretation of which method is primary,
recommended, or allowed. It displays policy, while server outcomes drive actual login screens.

## Persistence and migration

Add `recommended_method` to pending authentication persistence. Continue storing
`available_methods` as a non-empty set.

Add a migration that:

- removes or disables every persisted policy scheme containing `BACKUP_CODE`;
- removes `BACKUP_CODE` from legacy policy-rule factor arrays if those compatibility rows remain;
- preserves stored user recovery-code hashes;
- clears existing short-lived pending authentications so no pending row carries the old meaning;
- increments the policy version so clients and pending attempts cannot silently cross semantics.

Runtime authentication must read the persisted policy. Seeded defaults exist only to bootstrap a
new database through migrations; request handling must not silently replace a missing or invalid
stored policy with a different hard-coded policy.

## Failure and security behavior

- Wrong primary credentials retain the current generic response and account-enumeration
  protection.
- A factor method not stored in the pending authentication is refused even if that factor is
  enrolled for the user.
- Wrong, expired, replayed, or stale pending proofs issue no session.
- Recovery codes and factor secrets are never logged.
- Recovery codes remain single-use; successful full recovery invalidates the remainder of that
  generation.
- Session cookies are attached only after the selected primary-only path or a pending
  second-factor path completes.
- Safe relative-return validation applies equally to password, Google, factor, and recovery
  completion redirects.

## Existing components to reuse

- `AuthenticationPolicy` and `AuthenticationScheme` remain the versioned policy model.
- `AuthenticationCompleter` remains the common completion point for password and Google primary
  proofs, but loses the branch that forces any enrolled factor independently of schemes.
- `PendingAuthentication` remains the server-owned continuation and gains the recommended method.
- `SecondFactorMethodRegistry` continues to dispatch TOTP and email factor verification. Recovery
  codes leave this registry.
- Existing session issuance, session revocation, password replacement, factor stores, recovery-code
  storage, action-proof checks, safe-return handling, and frontend-shared controls are composed
  rather than replaced.

## Verification contract

Implementation verification must cover the behavior through the public contract without
reinstalling unchanged dependencies:

- API schema generation/check and affected Kotlin/TypeScript compilation;
- policy evaluator scenarios for primary-only, recommended factor, alternative factor, disabled
  primary, and no compatible scheme;
- pending verification with recommended and alternative methods;
- rejection of methods absent from `available_methods`;
- recovery-code absence from policy and MFA responses;
- atomic recovery behavior;
- app and admin rendering of the recommended method plus `Sign in another way`;
- local live sign-in using the existing cached toolchains and Docker build cache.

Automated tests are not added or run unless separately requested. Verification commands must reuse
the repository-local package and Gradle caches and must not reinstall dependencies whose lock state
has not changed.

## Acceptance criteria

1. The initial app and admin login screens show only policy-enabled, runtime-available primary
   methods.
2. Correct password or Google proof either issues a session immediately or returns one pending MFA
   contract; no hidden enrollment rule changes that result.
3. `recommended_method` belongs to `available_methods` and opens by default in both clients.
4. `Sign in another way` appears only when another returned method exists.
5. Verifying any method returned for that pending authentication can issue the session.
6. A method absent from the pending authentication cannot issue a session.
7. Users without an applicable enrolled factor complete a configured primary-only scheme and reach
   the authenticated destination.
8. Recovery codes can still be issued and replaced in user settings but never appear in admin
   schemes, sign-in options, MFA responses, or action-proof options.
9. A valid recovery code performs the defined account reset atomically and cannot be reused.
10. Policy changes made in admin are the rules observed by new server sign-in evaluations and by
    the dynamic login UI.
