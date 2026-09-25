import type { paths } from "tallyvane-api-contract";

export class AuthError extends Error {
    public constructor(
        public readonly status: number,
        public readonly retryAfter = 0,
        public readonly fieldErrors: Readonly<Record<string, string>> = {},
    ) {
        super();
        this.name = "AuthError";
    }
}

export type Factor = "email" | "totp" | "backup";
export interface AuthResult {
    status: "issued" | "requires_second_factor" | "requires_email_verification" | "requires_enrollment";
    pendingId?: string;
    challengeId?: string;
    availableMethods?: Factor[];
    requiredMethods?: Factor[];
    primaryMethod?: string;
}
export interface Enrollment { payload?: string; secret?: string; otpauthUri?: string; backupCodes?: string[] }
export interface Session { id: string; createdAt?: string; lastSeenAt?: string; expiresAt?: string; device?: string | { name?: string }; current?: boolean }
export interface Security { email: string; emailVerified: boolean; passwordEnabled: boolean; methods: { kind: Factor; enabled: boolean }[]; sessions?: Session[] }

type AuthRoute = Extract<keyof paths, `/auth/${string}`>;
type AuthPostRoute = {
    [Route in AuthRoute]: paths[Route] extends { post: infer Operation }
        ? Operation extends { requestBody: { content: { "application/json": unknown } } } ? Route : never
        : never;
}[AuthRoute];
type AuthPostPath = AuthPostRoute extends `/auth${infer RelativePath}` ? RelativePath : never;
type CamelCase<Key extends string> = Key extends `${infer Head}_${infer Tail}`
    ? `${Head}${Capitalize<CamelCase<Tail>>}`
    : Key;
type Camelized<Value> = Value extends readonly (infer Item)[]
    ? Camelized<Item>[]
    : Value extends object
        ? { [Key in keyof Value as Key extends string ? CamelCase<Key> : Key]: Camelized<Value[Key]> }
        : Value;
type RequestBody<Path extends AuthPostPath> = {
    [Route in AuthPostRoute]: Route extends `/auth${Path}`
        ? paths[Route] extends { post: { requestBody: { content: { "application/json": infer Body } } } }
            ? Camelized<Body>
            : never
        : never;
}[AuthPostRoute];
type PostArguments = {
    [Path in AuthPostPath]: [path: Path, body: RequestBody<Path>];
}[AuthPostPath];

function mapJsonKeys(value: unknown, mapKey: (key: string) => string): unknown {
    if (Array.isArray(value)) return value.map(item => mapJsonKeys(item, mapKey));
    if (value === null || typeof value !== "object") return value;
    return Object.fromEntries(Object.entries(value).map(([key, nested]) => [mapKey(key), mapJsonKeys(nested, mapKey)]));
}

function toWireJson(value: unknown): unknown {
    return mapJsonKeys(value, key => key.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`));
}

function fromWireJson(value: unknown): unknown {
    return mapJsonKeys(value, key => key.replace(/_([a-z])/g, (_match, letter: string) => letter.toUpperCase()));
}

export function createAuthClient(fetcher: typeof fetch = fetch) {
    async function request<T>(path: string, method: string, body?: unknown, extraHeaders?: Readonly<Record<string, string>>): Promise<T> {
        const headers = new Headers({ Accept: "application/json" });
        if (method !== "GET") {
            const csrf = await request<{ token: string }>("/csrf", "GET");
            if (!csrf.token) throw new AuthError(403);
            headers.set("X-CSRF-Token", csrf.token);
            headers.set("Content-Type", "application/json");
        }
        for (const [name, value] of Object.entries(extraHeaders ?? {})) headers.set(name, value);
        let response: Response;
        try {
            response = await fetcher(`/api/v1/auth${path}`, {
                method, headers, credentials: "same-origin", cache: "no-store",
                ...(body === undefined ? {} : { body: JSON.stringify(toWireJson(body)) }),
            });
        } catch {
            throw new AuthError(0);
        }
        const rawData: unknown = response.status === 204 ? undefined : await response.json().catch(() => undefined);
        const data = fromWireJson(rawData);
        if (!response.ok) {
            const problem = data && typeof data === "object" ? data as Record<string, unknown> : {};
            const rawErrors = problem.errors ?? problem.fieldErrors;
            const fieldErrors = rawErrors && typeof rawErrors === "object"
                ? Object.fromEntries(Object.entries(rawErrors).filter((entry): entry is [string, string] => typeof entry[1] === "string"))
                : {};
            const retryAfter = Number(response.headers.get("Retry-After")) || 0;
            throw new AuthError(response.status, retryAfter, fieldErrors);
        }
        return data as T;
    }
    return {
        get: <T>(path: string) => request<T>(path, "GET"),
        post: <T>(...args: PostArguments) => request<T>(args[0], "POST", args[1]),
        postWithHeaders: <T>(path: string, body: unknown, headers: Readonly<Record<string, string>>) =>
            request<T>(path, "POST", body, headers),
        refreshSession: () => request<{ status: string }>("/refresh", "POST"),
        remove: <T>(path: string) => request<T>(path, "DELETE"),
    };
}
export const authClient = createAuthClient();
