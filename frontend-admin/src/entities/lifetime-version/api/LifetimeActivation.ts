import type { Api } from "frontend-shared/api";
import { LifetimeConsequences } from "../model/LifetimeConsequences";
import type { Client } from "../model/LifetimeForm";

/** Putting one kept version in force: looking at what it would do first, then doing it. The same for a rollback. */
export class LifetimeActivation {
    private readonly api: Api;
    private readonly client: Client;
    private readonly number: number;

    public constructor(api: Api, client: Client, number: number) {
        this.api = api;
        this.client = client;
        this.number = number;
    }

    public async preview(): Promise<LifetimeConsequences> {
        return new LifetimeConsequences(
            await this.api.get("/lifetime-previews", { query: { client: this.client, number: this.number } }),
        );
    }

    public async confirm(comment: string): Promise<void> {
        await this.api.post("/lifetime-activations", { client: this.client, number: this.number, comment });
    }
}
