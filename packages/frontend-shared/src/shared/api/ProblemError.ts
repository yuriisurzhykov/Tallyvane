import type { components } from "./generated/schema";
import type { ApiResponse } from "./ApiRequest";

type Problem = components["schemas"]["Problem"];

const TYPE_PREFIX = "https://tallyvane.com/errors/";

/** The kinds of failure the server names (ADR-062). Anything else, including a proxy's own error page, is `other`. */
export type ProblemKind =
    | "malformed-request"
    | "validation-failed"
    | "forbidden"
    | "sign-in-required"
    | "session-expired"
    | "step-up-required"
    | "not-found"
    | "conflict"
    | "gone"
    | "slow-down"
    | "unavailable"
    | "internal"
    | "other";

const KINDS: readonly ProblemKind[] = [
    "malformed-request",
    "validation-failed",
    "forbidden",
    "sign-in-required",
    "session-expired",
    "step-up-required",
    "not-found",
    "conflict",
    "gone",
    "slow-down",
    "unavailable",
    "internal",
];

/**
 * A failed request, as one object that answers the questions a caller has instead of handing out the
 * response to be picked apart. The body is private, so a caller cannot start depending on a field the
 * contract does not promise.
 */
export class ProblemError extends Error {
    private readonly problem: Problem;
    private readonly retryAfter: string | null;

    private constructor(problem: Problem, retryAfter: string | null) {
        super(problem.title);
        this.name = "ProblemError";
        this.problem = problem;
        this.retryAfter = retryAfter;
    }

    public static from(response: ApiResponse): ProblemError {
        return new ProblemError(ProblemError.read(response), response.headers.get("retry-after"));
    }

    private static read(response: ApiResponse): Problem {
        const body = response.body;
        if (typeof body === "object" && body !== null && "type" in body && "title" in body) {
            return body as Problem;
        }
        return { type: "about:blank", title: `HTTP ${String(response.status)}`, status: response.status };
    }

    public kind(): ProblemKind {
        const type = this.problem.type;
        return KINDS.find((kind) => type === `${TYPE_PREFIX}${kind}`) ?? "other";
    }

    public hasStatus(status: number): boolean {
        return this.problem.status === status;
    }

    public isSessionExpired(): boolean {
        return this.kind() === "session-expired";
    }

    /** The person is signed in but must prove who they are again before this act (ADR-092). */
    public isStepUpRequired(): boolean {
        return this.kind() === "step-up-required";
    }

    public isSignInRequired(): boolean {
        return this.kind() === "sign-in-required";
    }

    /** A `409` without `Retry-After` means the work was carried out and its answer cannot be given again (ADR-086). */
    public isAnsweredBefore(): boolean {
        return this.hasStatus(409) && this.retryAfter === null;
    }

    /**
     * How many whole seconds the server asks the caller to wait (`Retry-After`), or `undefined` when it asks
     * for nothing, or in a form this client does not count (an HTTP date).
     */
    public retryAfterSeconds(): number | undefined {
        if (this.retryAfter === null || !/^\d+$/.test(this.retryAfter.trim())) {
            return undefined;
        }
        return Number(this.retryAfter.trim());
    }

    /** A code was checked and was wrong (`422`, field `code`). */
    public isWrongCode(): boolean {
        return this.hasStatus(422) && this.fieldCode("code") === "wrong-code";
    }

    /** What is wrong with one field of a `422`, or `undefined` when that field is fine. */
    public fieldCode(field: string): string | undefined {
        return this.problem.errors?.find((error) => error.field === field)?.code;
    }
}
