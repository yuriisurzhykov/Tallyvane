import type { Api } from "frontend-shared/api";
import type { IntentionKey } from "@/shared/idempotency";
import { LifetimeConsequences } from "../model/LifetimeConsequences";
import type { Client } from "../model/LifetimeForm";

/** Putting one kept version in force: looking at what it would do first, then doing it. The same for a rollback. */
export class LifetimeActivation {
    private readonly api: Api;
    private readonly client: Client;
    private readonly number: number;
    private readonly intention: IntentionKey;

    public constructor(api: Api, client: Client, number: number, intention: IntentionKey) {
        this.api = api;
        this.client = client;
        this.number = number;
        this.intention = intention;
    }

    public async preview(): Promise<LifetimeConsequences> {
        return new LifetimeConsequences(
            await this.api.get("/lifetime-previews", { query: { client: this.client, number: this.number } }),
        );
    }

    public async confirm(comment: string): Promise<void> {
        const request = { client: this.client, number: this.number, comment };
        await this.intention.send(request, (idempotencyKey) =>
            this.api.post("/lifetime-activations", request, { idempotencyKey }),
        );
    }
}
