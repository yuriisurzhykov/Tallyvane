import { ProblemError, type Api } from "frontend-shared/api";

/** Turns a finished Google sign-in into a session. */
export class Sessions {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    /**
     * Sent once per sign-in. If the server says it already answered (`409`, ADR-086: the cookie is not
     * replayable), the work was done, so the only question left is whether the session is really there.
     */
    public async open(): Promise<void> {
        try {
            await this.api.trigger("/sessions");
        } catch (failure) {
            if (failure instanceof ProblemError && failure.isAnsweredBefore()) {
                await this.api.get("/me");
                return;
            }
            throw failure;
        }
    }
}
