import type { components } from "tallyvane-api-contract";

export const AUTH_PROBLEM_TYPES = {
    unauthorized: "https://tallyvane.com/errors/unauthorized",
    forbidden: "https://tallyvane.com/errors/forbidden",
    stepUpRequired: "https://tallyvane.com/errors/step-up-required",
} as const;

export type ApiProblem = components["schemas"]["Problem"];
export type StepUpProblem = components["schemas"]["StepUpRequiredProblem"];
export type AccessDeniedProblem = components["schemas"]["ProblemWithoutAction"];

export type SessionProbeResult =
    | { readonly status: "authenticated" }
    | { readonly status: "unauthorized" }
    | { readonly status: "accessDenied"; readonly problem?: AccessDeniedProblem }
    | { readonly status: "unavailable" };
export type RefreshResult = "refreshed" | "rejected" | "unavailable";
export interface StepUpRequestTarget {
    readonly method: string;
    readonly path: string;
}

export type AuthSessionState =
    | { readonly status: "checking" }
    | { readonly status: "authenticated" }
    | { readonly status: "refreshing" }
    | { readonly status: "anonymous" }
    | { readonly status: "stepUpRequired"; readonly problem: StepUpProblem; readonly target: StepUpRequestTarget }
    | { readonly status: "accessDenied"; readonly problem?: AccessDeniedProblem; readonly target?: StepUpRequestTarget }
    | { readonly status: "unavailable"; readonly operation: "verification" | "refresh" };

export interface AuthSessionRuntimeOptions {
    readonly checkSession: () => Promise<SessionProbeResult>;
    readonly refreshSession: () => Promise<RefreshResult>;
    readonly lockName?: string;
    readonly onAnonymous?: () => void;
}

type RecoveryResult =
    | { readonly status: "authenticated" }
    | { readonly status: "anonymous" }
    | { readonly status: "accessDenied"; readonly problem?: AccessDeniedProblem }
    | { readonly status: "unavailable" };

const permittedTransitions: Readonly<Record<AuthSessionState["status"], readonly AuthSessionState["status"][]>> = {
    checking: ["authenticated", "refreshing", "anonymous", "accessDenied", "stepUpRequired", "unavailable"],
    authenticated: ["checking", "refreshing", "anonymous", "stepUpRequired", "accessDenied", "unavailable"],
    refreshing: ["authenticated", "anonymous", "accessDenied", "stepUpRequired", "unavailable"],
    anonymous: ["checking", "refreshing", "authenticated", "unavailable"],
    stepUpRequired: ["authenticated", "refreshing", "anonymous", "accessDenied", "unavailable"],
    accessDenied: ["checking", "refreshing", "authenticated", "anonymous", "stepUpRequired", "unavailable"],
    unavailable: ["checking", "refreshing", "authenticated", "anonymous"],
};

/**
 * Headless session state machine shared by each application. Route changes and rendering stay in
 * the app composition; this runtime owns verification, refresh coordination, and auth transitions.
 */
export class AuthSessionRuntime {
    private state: AuthSessionState = { status: "checking" };
    private readonly listeners = new Set<() => void>();
    private initialization: Promise<boolean> | undefined;
    private recovery: Promise<RecoveryResult> | undefined;
    private readonly lockName: string;
    private readonly checkSession: AuthSessionRuntimeOptions["checkSession"];
    private readonly refreshSession: AuthSessionRuntimeOptions["refreshSession"];
    private readonly onAnonymous: AuthSessionRuntimeOptions["onAnonymous"];
    private activeStepUp: { readonly action: string; readonly target: StepUpRequestTarget } | undefined;
    private pendingStepUpProof: { readonly action: string; readonly target: StepUpRequestTarget; readonly value: string; readonly expiresAt: number } | undefined;

    public constructor(options: AuthSessionRuntimeOptions) {
        this.checkSession = options.checkSession;
        this.refreshSession = options.refreshSession;
        this.lockName = options.lockName ?? "tallyvane.auth.refresh";
        this.onAnonymous = options.onAnonymous;
    }

    public getSnapshot = (): AuthSessionState => this.state;

    public subscribe = (listener: () => void): (() => void) => {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    };

    /** Verify an existing cookie session before private route content is released. */
    public initialize(): Promise<boolean> {
        if (this.state.status === "authenticated") return Promise.resolve(true);
        return this.verifySession();
    }

    /** Revalidate on route and history changes instead of trusting cached UI state. */
    public verifySession(options: { readonly silent?: boolean } = {}): Promise<boolean> {
        if (this.state.status === "stepUpRequired") return Promise.resolve(false);
        if (this.initialization) return this.initialization;

        const operation = options.silent && this.state.status === "authenticated"
            ? this.verifySilently()
            : this.establishSession();
        this.initialization = operation.finally(() => {
            this.initialization = undefined;
        });
        return this.initialization;
    }

    /** Retry one unauthorized protected request after one coordinated session recovery. */
    public recoverUnauthorized(): Promise<boolean> {
        return this.recoverSession().then((result) => result.status === "authenticated");
    }

    public requireStepUp(problem: StepUpProblem, target: StepUpRequestTarget): void {
        this.activeStepUp = { action: problem.action, target };
        this.pendingStepUpProof = undefined;
        this.transition({ status: "stepUpRequired", problem, target });
    }

    public denyAccess(problem: AccessDeniedProblem, target?: StepUpRequestTarget): void {
        this.transition({ status: "accessDenied", problem, ...(target ? { target } : {}) });
    }

    public completeAccessDenied(): void {
        if (this.state.status === "accessDenied" && this.state.target) this.transition({ status: "authenticated" });
    }

    public completeStepUp(proof: string): void {
        if (!this.activeStepUp || !proof) return;
        this.pendingStepUpProof = {
            action: this.activeStepUp.action,
            target: this.activeStepUp.target,
            value: proof,
            expiresAt: Date.now() + 5 * 60_000,
        };
        this.activeStepUp = undefined;
        if (this.state.status === "stepUpRequired") this.transition({ status: "authenticated" });
    }

    /** Attach a completed proof only to the user's next manual retry of the challenged request. */
    public takeStepUpProof(action: string, method: string, path: string): string | undefined {
        const pending = this.pendingStepUpProof;
        if (!pending) return undefined;
        if (pending.expiresAt <= Date.now()) {
            this.pendingStepUpProof = undefined;
            return undefined;
        }
        if (pending.action !== action || pending.target.method !== method.toUpperCase() || pending.target.path !== path) {
            return undefined;
        }
        this.pendingStepUpProof = undefined;
        return pending.value;
    }

    public markAuthenticated(): void {
        this.transition({ status: "authenticated" });
    }

    public markAnonymous(): void {
        this.activeStepUp = undefined;
        this.pendingStepUpProof = undefined;
        this.transition({ status: "anonymous" });
        this.onAnonymous?.();
    }

    public markUnavailable(operation: "verification" | "refresh"): void {
        this.transition({ status: "unavailable", operation });
    }

    private async establishSession(): Promise<boolean> {
        this.transition({ status: "checking" });
        const result = await this.checkSafely();
        if (result.status === "authenticated") {
            this.markAuthenticated();
            return true;
        }
        if (result.status === "accessDenied") {
            this.transition({ status: "accessDenied", ...(result.problem ? { problem: result.problem } : {}) });
            return false;
        }
        if (result.status === "unavailable") {
            this.markUnavailable("verification");
            return false;
        }
        return this.recoverUnauthorized();
    }

    private async verifySilently(): Promise<boolean> {
        const result = await this.checkSafely();
        if (result.status === "authenticated") return true;
        if (result.status === "unauthorized") return this.recoverUnauthorized();
        if (result.status === "accessDenied") {
            this.transition({ status: "accessDenied", ...(result.problem ? { problem: result.problem } : {}) });
            return false;
        }
        // A transient probe failure does not invalidate an already verified session.
        return true;
    }

    private recoverSession(): Promise<RecoveryResult> {
        if (this.recovery) return this.recovery;
        this.transition({ status: "refreshing" });

        const operation = this.withRefreshLock(async () => {
            // Another tab may have rotated the cookie while this tab waited for the lock.
            const current = await this.checkSafely();
            if (current.status === "authenticated") return { status: "authenticated" } as const;
            if (current.status === "accessDenied") {
                return { status: "accessDenied", ...(current.problem ? { problem: current.problem } : {}) } as const;
            }
            if (current.status === "unavailable") return { status: "unavailable" } as const;

            const refreshed = await this.refreshSafely();
            if (refreshed !== "refreshed") {
                // A different tab may have rotated the cookie while a best-effort lock was contended.
                const afterFailedRefresh = await this.checkSafely();
                if (afterFailedRefresh.status === "authenticated") return { status: "authenticated" } as const;
                if (afterFailedRefresh.status === "accessDenied") {
                    return { status: "accessDenied", ...(afterFailedRefresh.problem ? { problem: afterFailedRefresh.problem } : {}) } as const;
                }
                if (afterFailedRefresh.status === "unavailable") return { status: "unavailable" } as const;
                return refreshed === "rejected" ? { status: "anonymous" } as const : { status: "unavailable" } as const;
            }

            const afterRefresh = await this.checkSafely();
            if (afterRefresh.status === "authenticated") return { status: "authenticated" } as const;
            if (afterRefresh.status === "accessDenied") {
                return { status: "accessDenied", ...(afterRefresh.problem ? { problem: afterRefresh.problem } : {}) } as const;
            }
            return afterRefresh.status === "unavailable"
                ? { status: "unavailable" } as const
                : { status: "anonymous" } as const;
        }).catch((): RecoveryResult => ({ status: "unavailable" }));

        this.recovery = operation.finally(() => {
            this.recovery = undefined;
        });
        return this.recovery.then((result) => {
            if (result.status === "authenticated") this.markAuthenticated();
            else if (result.status === "anonymous") this.markAnonymous();
            else if (result.status === "accessDenied") this.transition(result);
            else this.markUnavailable("refresh");
            return result;
        });
    }

    private async checkSafely(): Promise<SessionProbeResult> {
        try {
            return await this.checkSession();
        } catch {
            return { status: "unavailable" };
        }
    }

    private async refreshSafely(): Promise<RefreshResult> {
        try {
            return await this.refreshSession();
        } catch {
            return "unavailable";
        }
    }

    private withRefreshLock<T>(operation: () => Promise<T>): Promise<T> {
        const lockManager = typeof navigator === "undefined" ? undefined : navigator.locks;
        if (lockManager) return lockManager.request(this.lockName, operation).then((result) => result as T);
        return withStorageLock(this.lockName, operation);
    }

    private transition(next: AuthSessionState): void {
        if (this.state.status !== next.status && !permittedTransitions[this.state.status].includes(next.status)) {
            throw new Error(`Invalid auth session transition: ${this.state.status} -> ${next.status}`);
        }
        this.state = next;
        this.listeners.forEach((listener) => listener());
    }
}

async function withStorageLock<T>(name: string, operation: () => Promise<T>): Promise<T> {
    const storage = safeLocalStorage();
    if (!storage) return operation();

    const key = `tallyvane.auth.lock.${name}`;
    const owner = createOwnerId();
    const deadline = Date.now() + 15_000;
    const leaseMilliseconds = 10_000;

    while (Date.now() < deadline) {
        const current = readLease(storage, key);
        if (!current || current.expiresAt <= Date.now()) {
            const claim = { owner, expiresAt: Date.now() + leaseMilliseconds };
            try {
                storage.setItem(key, JSON.stringify(claim));
            } catch {
                return operation();
            }
            if (readLease(storage, key)?.owner === owner) {
                const heartbeat = setInterval(() => {
                    if (readLease(storage, key)?.owner !== owner) return;
                    try {
                        storage.setItem(key, JSON.stringify({ owner, expiresAt: Date.now() + leaseMilliseconds }));
                    } catch {
                        // The lease remains bounded; the refresh result is still authoritative.
                    }
                }, Math.floor(leaseMilliseconds / 3));
                try {
                    return await operation();
                } finally {
                    clearInterval(heartbeat);
                    if (readLease(storage, key)?.owner === owner) storage.removeItem(key);
                }
            }
        }
        await delay(30 + Math.floor(Math.random() * 40));
    }

    throw new Error("Could not acquire the cross-tab auth refresh lock");
}

function readLease(storage: Storage, key: string): { owner: string; expiresAt: number } | undefined {
    try {
        const value = storage.getItem(key);
        if (!value) return undefined;
        const parsed: unknown = JSON.parse(value);
        if (!parsed || typeof parsed !== "object") return undefined;
        const lease = parsed as Record<string, unknown>;
        return typeof lease.owner === "string" && typeof lease.expiresAt === "number"
            ? { owner: lease.owner, expiresAt: lease.expiresAt }
            : undefined;
    } catch {
        return undefined;
    }
}

function safeLocalStorage(): Storage | undefined {
    try {
        return typeof localStorage === "undefined" ? undefined : localStorage;
    } catch {
        return undefined;
    }
}

function createOwnerId(): string {
    try {
        return crypto.randomUUID();
    } catch {
        return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    }
}

function delay(milliseconds: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, milliseconds));
}
