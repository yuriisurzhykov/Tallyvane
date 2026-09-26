import {
    AUTH_PROBLEM_TYPES,
    type AccessDeniedProblem,
    type ApiProblem,
    type AuthSessionRuntime,
    type StepUpProblem,
} from "./auth-session-runtime";

const API_PREFIX = "/api/v1/auth";
const sessionEndpoints = new Set(["/csrf", "/session", "/refresh"]);
const signInEndpoints = [
    /^\/login(?:\/|$)/u,
    /^\/mfa\/(?:verify|email\/request|required\/(?:enroll|confirm))(?:\/|$)/u,
    /^\/(?:register|registration|recovery)(?:\/|$)/u,
    /^\/password\/(?:forgot|reset)(?:\/|$)/u,
    /^\/google\/(?:start|callback)(?:\/|$)/u,
];

export type SessionAwareFetch = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

export interface AuthSessionTransportOptions {
    /** Identify protected API paths for this app; public and sign-in endpoints stay local. */
    readonly isProtectedRequest?: (path: string) => boolean;
    /** Map only proof-protected mutations; the proof is scoped to the exact endpoint and method. */
    readonly actionForRequest?: (path: string, method: string) => string | undefined;
}

/** Adds one shared RFC 9457 classification and refresh/retry policy around an app's API fetcher. */
export function createAuthSessionTransport(
    runtime: AuthSessionRuntime,
    fetcher: typeof fetch = fetch,
    options: AuthSessionTransportOptions = {},
): SessionAwareFetch {
    const matchesProtectedRequest = options.isProtectedRequest ?? isProtectedAuthRequest;
    const resolveAction = options.actionForRequest ?? actionForRequest;
    return async (input, init) => {
        const retryInput = cloneForRetry(input);
        const path = requestPath(input);
        const method = requestMethod(input, init);
        const suppliedHeaders = new Headers(init?.headers ?? (typeof Request !== "undefined" && input instanceof Request ? input.headers : undefined));
        const action = path ? resolveAction(path, method) : undefined;
        const stepUpProof = action && path && !suppliedHeaders.has("X-Action-Proof")
            ? runtime.takeStepUpProof(action, method, path)
            : undefined;
        let requestInit = init;
        if (stepUpProof) {
            const headers = new Headers(suppliedHeaders);
            headers.set("X-Action-Proof", stepUpProof);
            requestInit = { ...init, headers };
        }
        const response = await fetcher(input, requestInit);
        if (response.ok && (path === `${API_PREFIX}/logout` || path === `${API_PREFIX}/logout-all`)) {
            runtime.markAnonymous();
            return response;
        }
        const problem = await readProblem(response);
        if (!problem) return response;

        if (!path || !matchesProtectedRequest(path)) return response;

        if (response.status === 403 && problem.type === AUTH_PROBLEM_TYPES.stepUpRequired) {
            runtime.requireStepUp(problem as StepUpProblem, { method, path: path! });
            return response;
        }
        if (response.status === 403 && problem.type === AUTH_PROBLEM_TYPES.forbidden) {
            runtime.denyAccess(problem as AccessDeniedProblem, { method, path: path! });
            return response;
        }
        if (response.status !== 401 || problem.type !== AUTH_PROBLEM_TYPES.unauthorized) return response;

        if (!await runtime.recoverUnauthorized()) return response;

        const retriedResponse = await fetcher(retryInput, init);
        const retriedProblem = await readProblem(retriedResponse);
        if (!retriedProblem) return retriedResponse;

        if (retriedResponse.status === 401 && retriedProblem.type === AUTH_PROBLEM_TYPES.unauthorized) {
            runtime.markAnonymous();
        } else if (retriedResponse.status === 403 && retriedProblem.type === AUTH_PROBLEM_TYPES.stepUpRequired) {
            runtime.requireStepUp(retriedProblem as StepUpProblem, { method, path: path! });
        } else if (retriedResponse.status === 403 && retriedProblem.type === AUTH_PROBLEM_TYPES.forbidden) {
            runtime.denyAccess(retriedProblem as AccessDeniedProblem, { method, path: path! });
        }
        return retriedResponse;
    };
}

function requestMethod(input: RequestInfo | URL, init?: RequestInit): string {
    return (init?.method ?? (typeof Request !== "undefined" && input instanceof Request ? input.method : "GET")).toUpperCase();
}

function actionForRequest(path: string | undefined, method: string): string | undefined {
    if (!path || !isProtectedAuthRequest(path) || method === "GET" || method === "HEAD") return undefined;
    // The mapping intentionally names only endpoints whose backend handlers consume X-Action-Proof.
    if (path === `${API_PREFIX}/account/password`) return "CHANGE_PRIMARY_CREDENTIAL";
    if ([
        `${API_PREFIX}/mfa/enroll`, `${API_PREFIX}/mfa/disable`,
        `${API_PREFIX}/mfa/email/enroll`, `${API_PREFIX}/mfa/backup-codes`,
        `${API_PREFIX}/google/unlink`,
    ].includes(path)) return "MANAGE_SECOND_FACTORS";
    return undefined;
}

async function readProblem(response: Response): Promise<{ readonly type: string } & Partial<ApiProblem> | undefined> {
    if (response.ok) return undefined;
    const contentType = response.headers.get("Content-Type")?.split(";", 1)[0]?.trim().toLowerCase();
    if (contentType !== "application/problem+json" && contentType !== "application/json") return undefined;
    try {
        const body: unknown = await response.clone().json();
        if (!body || typeof body !== "object" || typeof (body as Record<string, unknown>).type !== "string") {
            return undefined;
        }
        return body as { readonly type: string } & Partial<ApiProblem>;
    } catch {
        return undefined;
    }
}

function requestPath(input: RequestInfo | URL): string | undefined {
    const rawUrl = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    try {
        const origin = typeof location === "undefined" ? "http://localhost" : location.origin;
        return new URL(rawUrl, origin).pathname;
    } catch {
        return undefined;
    }
}

function isProtectedAuthRequest(path: string | undefined): boolean {
    if (!path?.startsWith(`${API_PREFIX}/`)) return false;
    const endpoint = path.slice(API_PREFIX.length);
    if (sessionEndpoints.has(endpoint)) return false;
    return !signInEndpoints.some((pattern) => pattern.test(endpoint));
}

function cloneForRetry(input: RequestInfo | URL): RequestInfo | URL {
    return typeof Request !== "undefined" && input instanceof Request ? input.clone() : input;
}
