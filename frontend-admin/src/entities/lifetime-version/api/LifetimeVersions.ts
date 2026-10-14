import type { Api } from "frontend-shared/api";
import type { Client, LifetimeForm } from "../model/LifetimeForm";
import { LifetimeHistory } from "../model/LifetimeHistory";
import { LifetimeVersion } from "../model/LifetimeVersion";
import { IntentionKey } from "@/shared/idempotency";
import { LifetimeActivation } from "./LifetimeActivation";

/** The session lifetime versions on the server, and what can be done to them. Administrators' site only. */
export class LifetimeVersions {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    public async history(client: Client): Promise<LifetimeHistory> {
        const { versions } = await this.api.get("/lifetime-history", { query: { client } });
        return new LifetimeHistory(versions.map((wire) => new LifetimeVersion(wire)));
    }

    /**
     * Keeps the form as the next version for the client and says its number. A refusal throws; see `Rejection`.
     * Saying it again after an answer was lost does not keep a second version.
     */
    public async add(client: Client, form: LifetimeForm, comment: string, intention: IntentionKey): Promise<number> {
        const request = form.toRequest(client, comment);
        const made = await intention.send(request, (idempotencyKey) =>
            this.api.post("/lifetime-versions", request, { idempotencyKey }),
        );
        return made.number;
    }

    public activation(client: Client, number: number): LifetimeActivation {
        return new LifetimeActivation(this.api, client, number, new IntentionKey());
    }
}
