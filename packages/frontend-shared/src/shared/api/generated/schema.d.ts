/**
 * Generated from docs/openapi.yaml by scripts/generate-api-types.mjs. Do not edit: run `pnpm run api:generate`.
 */

export interface paths {
    "/health": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Aggregate health, for a human or an alert
         * @description Always answers 200: it is informational, and a 503 here would look to a monitor like the
         *     endpoint itself being down. The meaning is in the body.
         *
         *     Without the service token the body is `HealthSummary` — one field. With it, `HealthDetail`
         *     adds `ready` and the per-check breakdown. Dependency names describe what the system is
         *     built from, which is why an anonymous caller does not get them (ADR-055).
         */
        get: operations["healthAggregate"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/health/live": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Is the process alive
         * @description Consults no dependency, by construction — the handler is not given the reporter. A liveness
         *     probe that checked a dependency would turn that dependency's outage into a restart loop of
         *     a healthy process.
         *
         *     Answers 200 with `{"status":"up"}` whenever it answers at all. A dead process does not
         *     answer, which is the signal.
         */
        get: operations["healthLive"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/health/ready": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Should traffic come here
         * @description Readiness follows `required_for_readiness` on each check rather than the aggregate status,
         *     so a `degraded` application answers **200**: an unavailable optional dependency must not
         *     close the part of the product that does not need it.
         *
         *     503 rather than 500 when unready — "not now, try later" is the statement being made.
         */
        get: operations["healthReady"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/google-sign-in": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Start signing in with Google
         * @description Begins an attempt and answers where to send the browser. The attempt's secret travels in the
         *     `__Host-attempt` cookie set on this response: `HttpOnly`, `Secure`, `Path=/`, `SameSite=Lax`.
         *     Every sign-in starts as a login; a person Google turns out not to know is moved to a
         *     registration when they come back.
         *
         *     A response that sets a cookie is never stored for a repeat of its `Idempotency-Key`; a repeat
         *     is answered `409` without `Retry-After`.
         */
        post: operations["signInWithGoogle"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/google-step-up": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Start confirming a dangerous act with Google
         * @description Begins a confirmation and answers where to send the browser, exactly as `POST /google-sign-in`
         *     does, with the attempt's secret in the same `__Host-attempt` cookie. It is public because it only
         *     begins an attempt, which proves nothing by itself. It counts only when a signed-in person takes it
         *     with `POST /step-ups`.
         *
         *     When Google sends the browser back the redirect goes to `/step-up/continue`, never to the
         *     pages of a sign-in, and a Google account this application does not know is turned back instead
         *     of becoming a registration.
         *
         *     A response that sets a cookie is never stored for a repeat of its `Idempotency-Key`; a repeat
         *     is answered `409` without `Retry-After`.
         */
        post: operations["stepUpWithGoogle"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/google-return": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Where Google sends the browser back to
         * @description The one address registered with Google as the redirect URI. Reached by a redirect, so it never
         *     answers with an error document: every outcome is a `302` to a page of the application, and
         *     nothing in the request chooses which.
         *
         *     - a person with an account who was signing in: `/login/continue`
         *     - a person confirming a dangerous act (`POST /google-step-up`): `/step-up/continue`
         *     - a new person: `/welcome`, with a new `__Host-attempt` cookie for their registration
         *     - anyone else: `/login?problem=<reason>`, the cookie cleared. Reasons: `restart`, `expired`,
         *       `cancelled`, `refused`, `email-unverified`, `unavailable`.
         */
        get: operations["signInReturnFromGoogle"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/welcome": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * What the welcome form starts from
         * @description The name and address Google gave the person registering, to prefill the form. Never cached.
         *     `404` when the browser has no registration to finish: no cookie, one that expired, or one
         *     already used.
         */
        get: operations["showWelcome"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/registration": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Finish the welcome form
         * @description Creates the account of the person registering, with the name they chose, if they agreed to the
         *     privacy policy. Submitting twice finds the same account. Registering grants no access: the
         *     sign-in it completes waits to be redeemed for a session.
         *
         *     `422` names the field: `agreed` (`consent-required`) or `name` (`name-invalid`). `404` when the
         *     browser has no registration to finish.
         */
        post: operations["register"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/sign-in": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Where a sign-in or a confirmation stands
         * @description What the attempt named by the `__Host-attempt` cookie waits for, so the page can show the next step:
         *     a code from the authenticator, a recovery code, nothing more (it is `complete` and is redeemed with
         *     `POST /sessions` or taken with `POST /step-ups`), or that it is over. Answers from the policy in force
         *     and the account as they are now. Never cached. `404` when the browser has no sign-in or confirmation:
         *     no cookie, one that expired, or one already used (ADR-093).
         */
        get: operations["showSignIn"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/second-factor-codes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Answer the second step of a sign-in
         * @description Gives the attempt named by the `__Host-attempt` cookie a code from the person's authenticator, or a
         *     recovery code, when `GET /sign-in` says it waits for one (ADR-093). What is proved is only recorded
         *     on the attempt; redeeming it for a session, or taking it as a fresh proof, is the next request.
         *     A recovery code is spent, and spending one retires the TOTP seed. Typing a recovery code ignores
         *     case, dashes and spaces.
         *
         *     - `204` a code from the authenticator was right.
         *     - `200` a recovery code was right; `recovery_codes_remaining` counts what is left.
         *     - `422` the answer was wrong (`code` is `wrong-code`). `Retry-After` says how long the next answer is
         *       not looked at, once a pause applies. Five wrong answers end the attempt, and the account has a
         *       count of its own that outlives any one attempt: it is a pause that grows to five minutes, never a
         *       lockout.
         *     - `429` a pause is running and the answer was not even checked; `Retry-After` is the time left.
         *     - `410` there is no sign-in to answer: none was begun, it ran out, or its last wrong answer ended it.
         *       The person starts again.
         *     - `409` nothing more is wanted from this attempt, or another request changed it first; ask where it
         *       stands with `GET /sign-in`.
         *     - `400` `kind` is not `totp` or `recovery_code`.
         */
        post: operations["answerSecondFactor"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/second-factor": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * What the signed-in person has set up as a second factor
         * @description `off` (nothing, or a set-up begun and not confirmed), `active`, or `retired`: the seed stopped
         *     working because a recovery code was spent, and only the remaining recovery codes do until the person
         *     turns TOTP on again. `recovery_codes_remaining` counts the codes still unspent. Never cached.
         */
        get: operations["showSecondFactor"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/totp-enrollments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Begin turning TOTP on
         * @description Makes a new seed and tells it once, as text to type and as the `otpauth://` address a QR code carries.
         *     Nothing is protected until the first code is typed (`POST /totp-confirmations`); beginning again
         *     starts over with another seed. `409` when TOTP is already on: it is turned off first.
         *
         *     A dangerous act: it asks for a recent proof of who the person is (ADR-092), since a second factor set
         *     up by somebody else locks the owner out. A person whose last proof is older is answered `403` with the
         *     problem type `step-up-required`, and goes on after `POST /step-ups`.
         *
         *     The answer carries the seed, so it is never stored for a repeat of its `Idempotency-Key`; a repeat is
         *     answered `409` without `Retry-After`. Never cached.
         */
        post: operations["beginTotp"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/totp-confirmations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Type the first code, which turns TOTP on
         * @description The first code the authenticator shows. A right one makes the enrolment active and tells ten recovery
         *     codes, once, replacing any set the person had. `422` when the code was wrong (`code` is `wrong-code`);
         *     the set-up stays waiting and no limit applies, since whoever began it already knows the seed. `409`
         *     when nothing was begun or TOTP is already on.
         *
         *     It needs only a signed-in person: the code is itself the proof of holding the seed. The answer carries
         *     the recovery codes, so it is never stored for a repeat of its `Idempotency-Key`. Never cached.
         */
        post: operations["confirmTotp"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/totp-enrollment": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Turn TOTP off
         * @description Removes the seed, whatever its standing, and the recovery codes with it. `409` when there is nothing
         *     to remove.
         *
         *     A dangerous act: it asks for a recent proof of who the person is (ADR-092). A person whose last
         *     proof is older is answered `403` with the problem type `step-up-required`, and goes on after
         *     `POST /step-ups`.
         */
        delete: operations["disableTotp"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/recovery-codes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Ask for ten new recovery codes
         * @description Replaces every recovery code the person had, spent or not, and tells the new ones once. `409` unless
         *     TOTP is active.
         *
         *     A dangerous act: it asks for a recent proof of who the person is (ADR-092). A person whose last
         *     proof is older is answered `403` with the problem type `step-up-required`, and goes on after
         *     `POST /step-ups`.
         *
         *     The answer carries the codes, so it is never stored for a repeat of its `Idempotency-Key`. Never cached.
         */
        post: operations["regenerateRecoveryCodes"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/sessions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Exchange a completed sign-in for a session
         * @description Public: the person asking is not signed in yet. What they hold instead is the cookie of a sign-in
         *     that is complete, and it is exchanged once. The answer sets `__Host-session` and takes
         *     `__Host-attempt` away.
         *
         *     `404` when there is nothing complete to exchange: no cookie, one that expired, one already
         *     exchanged, or a sign-in that is not finished (a registration waiting for its form). A response
         *     that sets a cookie is never replayed for a repeated `Idempotency-Key`, so a repeat is told `409`
         *     without `Retry-After`: the work was carried out, so read the current state instead.
         */
        post: operations["openSession"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/step-ups": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Prove who you are again, for a dangerous act
         * @description For a signed-in person, including one whose last proof has gone stale: this is how they get a
         *     fresh one. Takes the finished confirmation named by the `__Host-attempt` cookie (see
         *     `POST /google-step-up`) and moves the time of the last proof on the session the browser presents.
         *     The session is the same one: its secret does not change and it does not live longer.
         *
         *     - `401` no session, or one that has ended.
         *     - `403` the confirmation was another person's. It is spent all the same, and the session is
         *       unchanged. The page starts again.
         *     - `409` there is no finished confirmation to take: no cookie, one that expired, one already
         *       taken, or one still waiting for a code.
         *
         *     A response that sets a cookie is never replayed for a repeated `Idempotency-Key`.
         */
        post: operations["confirmStepUp"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/session": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Sign out
         * @description Public, so that a person whose session has already ended can still clear their cookie. Forgets the
         *     session the browser presents and tells the browser to forget its cookie. Always `204`: a session
         *     that was good, one that had ended and none at all all end the same way, with the person signed out.
         */
        delete: operations["signOut"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Who the signed-in person is
         * @description Never cached. `401` `sign-in-required` when no session is presented; `401` `session-expired` when
         *     the session has ended or names an account that no longer exists.
         */
        get: operations["whoAmI"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/security-activity": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * The signed-in person's security journal
         * @description Their entries, the newest first, thirty at a time. A page that has more after it carries `next`,
         *     which is passed back as `before` to ask for the following page; the last page has none.
         *     `400` when `before` is not a positive whole number, which is all a cursor is: the position after which to continue. Nobody's entries but the signed-in person's are
         *     ever shown, and nothing here lets them change one. Never cached.
         *
         *     A device is the one the entry happened on, as it was then: renaming or signing out a device later
         *     does not change what the journal says. An entry has no device when the session it came from began
         *     before the journal existed.
         */
        get: operations["showSecurityActivity"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * The devices the person is signed in on
         * @description The live sessions of the signed-in person, the most recently used first, the one asking marked
         *     `current`. A session past its lifetimes under the policy in force is not listed. Never cached.
         *
         *     A browser says its kind and its system and no more, so two of a person's browsers on the same
         *     system look alike; `PUT /device-names/{id}` lets them tell the devices apart.
         */
        get: operations["listDevices"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Sign out on one device
         * @description Ends the session `id`, which `GET /devices` lists. It may be the one asking, which is then the same
         *     as signing out here. `404` when `id` is not a session of the signed-in person: one that never was,
         *     one that has ended, a stranger's and a value that is not an id all answer alike.
         *
         *     A dangerous act: it asks for a recent proof of who the person is (ADR-092). A person whose last
         *     proof is older is answered `403` with the problem type `step-up-required`, and goes on after
         *     `POST /step-ups`.
         */
        delete: operations["signOutOnDevice"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-names/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Give a device a name
         * @description Names the session `id` of the signed-in person. Naming twice leaves the last name. `422` names the
         *     field: `name` (`name-invalid`). `404` as for `DELETE /device/{id}`.
         */
        put: operations["nameDevice"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/other-devices": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Sign out everywhere but here
         * @description Ends every session of the signed-in person except the one asking. `204` whether there was
         *     another session or not.
         *
         *     A dangerous act: it asks for a recent proof of who the person is (ADR-092). A person whose last
         *     proof is older is answered `403` with the problem type `step-up-required`, and goes on after
         *     `POST /step-ups`.
         */
        delete: operations["signOutOnOtherDevices"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /**
         * @description Three states, not two. `degraded` means working with something missing — the distinction
         *     exists so that an unavailable optional dependency does not read as an outage.
         * @enum {string}
         */
        HealthStatus: "up" | "degraded" | "down";
        /**
         * @description What an unauthorised caller gets. One field, and a separate schema from `HealthDetail`
         *     rather than a filtered view of it: a shape with one field cannot leak a second.
         */
        HealthSummary: {
            status: components["schemas"]["HealthStatus"];
        };
        HealthDetail: {
            status: components["schemas"]["HealthStatus"];
            /**
             * @description Kept although it is derivable from the checks, because the derivation is not obvious —
             *     a `down` optional dependency leaves the application ready — and making a reader redo
             *     that logic invites getting it wrong.
             */
            ready: boolean;
            checks: components["schemas"]["HealthChecked"][];
        };
        HealthChecked: {
            /**
             * @description Stable across releases, because alert rules reference it.
             * @example database
             * @example schema
             */
            name: string;
            status: components["schemas"]["HealthStatus"];
            /** @description Whole milliseconds, so an alert threshold reads `3` rather than `0.003`. */
            took_ms: number;
            cause?: components["schemas"]["HealthCause"];
        };
        /**
         * @description Why a check is not `up`, discriminated by `kind` — not `type`, because `threw` already has
         *     a field of that name and §11.6 spends `type` on a problem URI.
         *
         *     Two internal causes have no representation here at all: an aggregate over ailing
         *     dependencies, and a schema behind the code. Both name what the system is made of, and
         *     ADR-055 withholds that from every answer including an authorised one.
         */
        HealthCause: components["schemas"]["CauseRefused"] | components["schemas"]["CauseOverran"] | components["schemas"]["CauseThrew"];
        CauseRefused: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            kind: "refused";
            /** @description The check's own words. The one cause whose text we write rather than a library. */
            says: string;
        };
        CauseOverran: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            kind: "overran";
            /** @description The bound the check did not answer inside. */
            bound_ms: number;
        };
        CauseThrew: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            kind: "threw";
            /**
             * @description The exception's type and nothing else. A message carries hosts, ports and occasionally
             *     credentials (§17), so there is no field for one.
             * @example SQLException
             */
            type: string;
        };
        Problem: {
            /**
             * Format: uri
             * @description Stable identifier of the *kind* of failure, and the field a client branches on. It
             *     survives rewording of `title` and `detail`.
             *
             *     `about:blank` means what RFC 9457 section 4.2.1 says it means: the failure has no
             *     semantics beyond its status code, and `title` is that code's own phrase. It is what a
             *     status the server produced without reaching any endpoint carries — an unknown path, a
             *     method an endpoint does not accept. A client telling "no such resource" apart from "no
             *     such address" branches on this field, which is the reason it is not one value for both.
             * @example https://tallyvane.com/errors/validation-failed
             * @example https://tallyvane.com/errors/malformed-request
             * @example https://tallyvane.com/errors/internal
             * @example https://tallyvane.com/errors/gone
             * @example https://tallyvane.com/errors/slow-down
             * @example about:blank
             */
            type: string;
            /** @description The kind in human words, identical for every occurrence of one `type`. */
            title: string;
            /** @description Repeated in the body so the document stands alone when forwarded or logged. */
            status: number;
            /** @description This occurrence in human words. Never a driver's message. */
            detail?: string;
            /** @description Per-field detail for a validation failure; absent otherwise. */
            errors?: components["schemas"]["FieldError"][];
            /**
             * @description Added by the renderer, so it is on every problem body including a 500. The same id is
             *     in the `traceparent` header and on the log lines of that request.
             */
            trace_id?: string;
        };
        FieldError: {
            /** @description The name as the client sent it — `salary_min_cents`, not `salaryMinCents`. */
            field: string;
            /**
             * @description What is wrong, in a form a frontend can branch on. A free string on purpose: closing
             *     this set would put every module's validation vocabulary in the platform.
             * @example range.invalid
             * @example required
             */
            code: string;
        };
        SignInStarted: {
            /**
             * Format: uri
             * @description Google's sign-in page, with this attempt's `state`, `nonce` and PKCE challenge.
             */
            authorization_url: string;
        };
        SignInState: {
            /**
             * @description `awaiting` an answer from `factors`; `paused` as `awaiting`, but a pause is running (`retry_after`);
             *     `complete` everything wanted is proved; `restricted` everything is proved but the person must set
             *     up a factor first; `exhausted` the last wrong answer ended the attempt; `expired` it outlived its
             *     lifetime. The last two mean the person starts again.
             * @enum {string}
             */
            state: "awaiting" | "paused" | "complete" | "restricted" | "exhausted" | "expired";
            /** @description The kinds of answer wanted. Empty unless the state is `awaiting` or `paused`. */
            factors: ("totp" | "recovery_code")[];
            /** @description Seconds a `paused` attempt must still wait. */
            retry_after?: number;
        };
        SecondFactorCode: {
            /** @enum {string} */
            kind: "totp" | "recovery_code";
            /** @description Six digits from the authenticator, or a recovery code as it was written down. */
            code: string;
        };
        SecondFactorCodeAccepted: {
            recovery_codes_remaining: number;
        };
        SecondFactorState: {
            /** @enum {string} */
            standing: "off" | "active" | "retired";
            recovery_codes_remaining: number;
        };
        TotpStarted: {
            /** @description The seed in base32, to type into an authenticator app. Told once. */
            key: string;
            /** @description The `otpauth://` address a QR code carries. Told once. */
            uri: string;
        };
        FirstCode: {
            /** @description The six digits the authenticator shows now. */
            code: string;
        };
        RecoveryCodesIssued: {
            /**
             * @description Ten codes such as `ABCDE-FGHJK`, each valid once. Told once, and kept only as digests: the person
             *     writes them down now.
             */
            recovery_codes: string[];
        };
        Welcomed: {
            /** @description The name Google has for the person, to be edited. */
            name: string;
            /** @description The address Google verified. Shown, not editable, because it is the one that was verified. */
            email: string;
        };
        Me: {
            /**
             * Format: uuid
             * @description The account.
             */
            id: string;
            /** @description The name the person is called by. */
            name: string;
        };
        Registering: {
            /** @description The name the person chose. 1 to 80 characters, no control characters. */
            name: string;
            /**
             * @description Whether the person agreed to the privacy policy. Without it nothing is created.
             * @default false
             */
            agreed: boolean;
        };
        SecurityActivity: {
            entries: components["schemas"]["SecurityEntry"][];
            /** @description The cursor of the next page. Absent on the last one. Opaque to a client. */
            next?: string;
        };
        SecurityEntry: {
            /**
             * @description What happened. A client branches on it.
             * @enum {string}
             */
            kind: "signed_in" | "totp_turned_on" | "totp_turned_off" | "recovery_code_spent" | "recovery_codes_reissued" | "other_devices_signed_out" | "guessing_stopped";
            /** Format: date-time */
            occurred_at: string;
            /** @description Where it happened, when that is known. A recovery code spent and a guess stopped happen before any session exists, so they have none. */
            device?: components["schemas"]["SecurityEntryDevice"];
            /**
             * @description Only on `signed_in`: the account had no earlier sign-in from the same browser, system and class
             *     (phone or not). A hint, not a protection: the name does not count, and a second browser of the same
             *     kind on the same system is not told apart.
             */
            first_from_device: boolean;
            /** @description How many recovery codes are left. Only on `recovery_code_spent`. */
            codes_left?: number;
        };
        SecurityEntryDevice: {
            /** @enum {string} */
            browser: "chrome" | "edge" | "firefox" | "opera" | "safari" | "other";
            /** @enum {string} */
            platform: "windows" | "macos" | "linux" | "android" | "ios" | "chromeos" | "other";
            mobile: boolean;
            /** @description The name the person had given the device when it happened. Absent when they had given none. */
            name?: string;
        };
        Devices: {
            devices: components["schemas"]["Device"][];
        };
        Device: {
            /**
             * Format: uuid
             * @description The session. Names it for `DELETE /device/{id}` and `PUT /device-names/{id}`; it is not a secret.
             */
            id: string;
            /**
             * @description As far as the `User-Agent` of the request that began the session says.
             * @enum {string}
             */
            browser: "chrome" | "edge" | "firefox" | "opera" | "safari" | "other";
            /**
             * @description The system, as far as the `User-Agent` says. Not the machine.
             * @enum {string}
             */
            platform: "windows" | "macos" | "linux" | "android" | "ios" | "chromeos" | "other";
            /** @description A phone or a tablet. */
            mobile: boolean;
            /** @description What the person called the device. Absent until they do. */
            name?: string;
            /**
             * Format: date-time
             * @description When the person proved who they are.
             */
            signed_in_at: string;
            /**
             * Format: date-time
             * @description Kept to the minute.
             */
            last_active_at: string;
            /** @description Whether this is the session asking. */
            current: boolean;
        };
        DeviceNaming: {
            /** @description 1 to 60 characters once trimmed, no control characters. */
            name: string;
        };
    };
    responses: {
        /**
         * @description Any failure, in one shape (§11.6, RFC 9457). A route never invents a status or a `type`:
         *     both come from a closed set in the platform, so a client can branch on `type` and expect
         *     it to stay put (ADR-062).
         *
         *     This includes the statuses the server produces before any endpoint is reached — an unknown
         *     path, a method an endpoint does not accept. Those carry `about:blank` and no `detail`, and
         *     they are not listed per operation below, because they are not any one operation's answer.
         */
        Problem: {
            headers: {
                traceparent: components["headers"]["Traceparent"];
                [name: string]: unknown;
            };
            content: {
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
    };
    parameters: never;
    requestBodies: never;
    headers: {
        /**
         * @description `__Host-attempt=<secret>; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=900`, or the same with
         *     `Max-Age=0` to make the browser forget it. `__Host-` makes the browser refuse it unless it is
         *     `Secure`, has `Path=/` and names no `Domain`.
         */
        AttemptCookie: string;
        /**
         * @description `__Host-session=<secret>; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=7776000` when a session
         *     begins (the longest a session can be allowed to live, so a lifetime loosened later still reaches it),
         *     or the same with `Max-Age=0` to make the browser forget it. `__Host-` makes the browser refuse it
         *     unless it is `Secure`, has `Path=/` and names no `Domain`. A response that sets it also sets
         *     `__Host-attempt` with `Max-Age=0`.
         */
        SessionCookie: string;
        /**
         * @description Always `no-store`. Cloudflare sits in front of this application, and a 200 with no cache
         *     directives is a legitimate thing to cache — after which a cheerful "up" would outlive the
         *     truth.
         */
        NoStore: "no-store";
        /**
         * @description Whole seconds the person must wait before the next answer is looked at, rounded up and never fewer
         *     than one.
         */
        RetryAfter: number;
        /**
         * @description W3C Trace Context. Present on every response, including ones with no body. Quote it when
         *     reporting a problem: the same id is on the log lines of that request.
         */
        Traceparent: string;
    };
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    healthAggregate: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The aggregate. Shape depends on whether the service token was accepted. */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HealthSummary"] | components["schemas"]["HealthDetail"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    healthLive: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The process is answering. */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HealthSummary"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    healthReady: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Ready for traffic. May still be `degraded`. */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HealthSummary"];
                };
            };
            /** @description A dependency required for readiness is down. */
            503: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HealthSummary"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    signInWithGoogle: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Where to send the browser. */
            200: {
                headers: {
                    "Set-Cookie": components["headers"]["AttemptCookie"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SignInStarted"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    stepUpWithGoogle: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Where to send the browser. */
            200: {
                headers: {
                    "Set-Cookie": components["headers"]["AttemptCookie"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SignInStarted"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    signInReturnFromGoogle: {
        parameters: {
            query?: {
                /** @description The authorization code. Absent when the person said no. */
                code?: string;
                /** @description The `state` this sign-in sent to Google, which must match. */
                state?: string;
                /** @description Google's reason when it sent no code. Not read; its absence of a code is the signal. */
                error?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description On to a page of the application. */
            302: {
                headers: {
                    Location?: string;
                    "Set-Cookie": components["headers"]["AttemptCookie"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    showWelcome: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description What Google said. */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Welcomed"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    register: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Registering"];
            };
        };
        responses: {
            /** @description The account exists. */
            204: {
                headers: {
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content?: never;
            };
            default: components["responses"]["Problem"];
        };
    };
    showSignIn: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Where the attempt stands. */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SignInState"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    answerSecondFactor: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SecondFactorCode"];
            };
        };
        responses: {
            /** @description A recovery code was spent. */
            200: {
                headers: {
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SecondFactorCodeAccepted"];
                };
            };
            /** @description The code was right. */
            204: {
                headers: {
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The answer was wrong. */
            422: {
                headers: {
                    "Retry-After": components["headers"]["RetryAfter"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /** @description A pause is running. */
            429: {
                headers: {
                    "Retry-After": components["headers"]["RetryAfter"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    showSecondFactor: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description What is set up. */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SecondFactorState"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    beginTotp: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The seed, told once. */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TotpStarted"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    confirmTotp: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["FirstCode"];
            };
        };
        responses: {
            /** @description TOTP is on; the recovery codes, told once. */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RecoveryCodesIssued"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    disableTotp: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description TOTP is off. */
            204: {
                headers: {
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content?: never;
            };
            default: components["responses"]["Problem"];
        };
    };
    regenerateRecoveryCodes: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The new codes, told once. */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RecoveryCodesIssued"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    openSession: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The session exists, and the browser holds it. */
            204: {
                headers: {
                    "Set-Cookie": components["headers"]["SessionCookie"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content?: never;
            };
            default: components["responses"]["Problem"];
        };
    };
    confirmStepUp: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /**
             * @description The session counts the new proof. `__Host-attempt` is set with `Max-Age=0`, since the
             *     confirmation is spent.
             */
            204: {
                headers: {
                    "Set-Cookie": components["headers"]["AttemptCookie"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content?: never;
            };
            default: components["responses"]["Problem"];
        };
    };
    signOut: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The person is signed out. */
            204: {
                headers: {
                    "Set-Cookie": components["headers"]["SessionCookie"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content?: never;
            };
            default: components["responses"]["Problem"];
        };
    };
    whoAmI: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The account and the name the person is called by. */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Me"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    showSecurityActivity: {
        parameters: {
            query?: {
                /** @description The `next` of the page before, to continue after it. Absent for the newest entries. */
                before?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The page. */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SecurityActivity"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    listDevices: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The devices. */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Devices"];
                };
            };
            default: components["responses"]["Problem"];
        };
    };
    signOutOnDevice: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The session has ended. */
            204: {
                headers: {
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content?: never;
            };
            default: components["responses"]["Problem"];
        };
    };
    nameDevice: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeviceNaming"];
            };
        };
        responses: {
            /** @description The device has the name. */
            204: {
                headers: {
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content?: never;
            };
            default: components["responses"]["Problem"];
        };
    };
    signOutOnOtherDevices: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Every other session has ended. */
            204: {
                headers: {
                    traceparent: components["headers"]["Traceparent"];
                    [name: string]: unknown;
                };
                content?: never;
            };
            default: components["responses"]["Problem"];
        };
    };
}
