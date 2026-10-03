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
         *     - a person with an account: `/login/continue`
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
         *     Not yet asked to confirm with a fresh factor, which ADR-079 wants of this action: the confirmation
         *     arrives with the second factor.
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
         *     or the same with `Max-Age=0` to make the browser forget it. `__Host-` makes the browser refuse it unless it is `Secure`, has `Path=/` and names no `Domain`. A response that sets it also
         *     sets `__Host-attempt` with `Max-Age=0`.
         */
        SessionCookie: string;
        /**
         * @description Always `no-store`. Cloudflare sits in front of this application, and a 200 with no cache
         *     directives is a legitimate thing to cache — after which a cheerful "up" would outlive the
         *     truth.
         */
        NoStore: "no-store";
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
