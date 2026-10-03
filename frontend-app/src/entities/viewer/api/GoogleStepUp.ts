import type { Api } from "frontend-shared/api";

/** Starts a confirmation with Google, for a person who is already signed in, and says where to send them. */
export class GoogleStepUp {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    public async begin(): Promise<string> {
        const started = await this.api.trigger("/google-step-up");
        return started.authorization_url;
    }
}
