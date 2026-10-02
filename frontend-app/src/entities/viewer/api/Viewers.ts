import { ProblemError, type Api } from "frontend-shared/api";
import { Viewer } from "../model/Viewer";

/** Asks the server who is signed in. */
export class Viewers {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    /** The signed-in person, or `undefined` when nobody is (no session, or one that ended). */
    public async current(): Promise<Viewer | undefined> {
        try {
            const me = await this.api.get("/me");
            return new Viewer(me.id, me.name);
        } catch (failure) {
            if (failure instanceof ProblemError && (failure.isSignInRequired() || failure.isSessionExpired())) {
                return undefined;
            }
            throw failure;
        }
    }
}
