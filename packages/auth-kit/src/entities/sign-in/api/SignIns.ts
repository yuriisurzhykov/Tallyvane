import { ProblemError, type Api } from "frontend-shared/api";
import { CodeAnswer } from "../model/CodeAnswer";
import type { CodeKind } from "../model/CodeKind";
import { SignInState } from "../model/SignInState";

/** The sign-in or confirmation in progress in this browser, and the second step it may ask for. */
export class SignIns {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    /** Where the attempt stands, or `undefined` when the browser has none: no cookie, one that ran out, or one already used. */
    public async state(): Promise<SignInState | undefined> {
        try {
            return new SignInState(await this.api.get("/sign-in"));
        } catch (failure) {
            if (failure instanceof ProblemError && failure.kind() === "not-found") {
                return undefined;
            }
            throw failure;
        }
    }

    /**
     * Gives the attempt a code. A wrong code, a pause, an attempt that is over and one that changed are
     * answers, not failures; anything else (the network, the server) is thrown. Every call is a new
     * intention, so the chain gives it a key of its own: a retyped code is not a replay.
     */
    public async answer(kind: CodeKind, code: string): Promise<CodeAnswer> {
        try {
            const body = await this.api.post("/second-factor-codes", { kind, code });
            return CodeAnswer.accepted(body?.recovery_codes_remaining);
        } catch (failure) {
            if (!(failure instanceof ProblemError)) {
                throw failure;
            }
            if (failure.isWrongCode()) {
                return CodeAnswer.wrong(failure.retryAfterSeconds() ?? 0);
            }
            if (failure.hasStatus(429)) {
                return CodeAnswer.paused(failure.retryAfterSeconds() ?? 1);
            }
            if (failure.hasStatus(410) || failure.kind() === "not-found") {
                return CodeAnswer.over();
            }
            if (failure.hasStatus(409)) {
                return CodeAnswer.changed();
            }
            throw failure;
        }
    }
}
