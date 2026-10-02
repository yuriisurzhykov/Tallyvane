import type { Api } from "frontend-shared/api";

/** Creates the account for the person who just signed in with Google. */
export class Registrations {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    /** Every call is a new intention, so every call gets its own key (a corrected form is not a replay). */
    public async register(name: string, agreed: boolean): Promise<void> {
        await this.api.post("/registration", { name, agreed });
    }
}
