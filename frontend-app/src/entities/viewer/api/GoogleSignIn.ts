import type { Api } from "frontend-shared/api";

/** Starts a Google sign-in on the server and says where to send the person. */
export class GoogleSignIn {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    public async begin(): Promise<string> {
        const started = await this.api.trigger("/google-sign-in");
        return started.authorization_url;
    }
}
