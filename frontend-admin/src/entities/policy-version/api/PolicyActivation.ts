import type { Api } from "frontend-shared/api";
import type { IntentionKey } from "@/shared/idempotency";
import { PolicyConsequences } from "../model/PolicyConsequences";
import type { Purpose } from "../model/PolicyForm";

/** Putting one kept version in force: looking at what it would do first, then doing it. The same for a rollback. */
export class PolicyActivation {
    private readonly api: Api;
    private readonly purpose: Purpose;
    private readonly number: number;
    private readonly intention: IntentionKey;

    public constructor(api: Api, purpose: Purpose, number: number, intention: IntentionKey) {
        this.api = api;
        this.purpose = purpose;
        this.number = number;
        this.intention = intention;
    }

    public async preview(): Promise<PolicyConsequences> {
        return new PolicyConsequences(
            await this.api.get("/policy-previews", { query: { purpose: this.purpose, number: this.number } }),
        );
    }

    public async confirm(comment: string): Promise<void> {
        const request = { purpose: this.purpose, number: this.number, comment };
        await this.intention.send(request, (idempotencyKey) =>
            this.api.post("/policy-activations", request, { idempotencyKey }),
        );
    }
}
