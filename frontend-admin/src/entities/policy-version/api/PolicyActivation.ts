import type { Api } from "frontend-shared/api";
import { PolicyConsequences } from "../model/PolicyConsequences";
import type { Purpose } from "../model/PolicyForm";

/** Putting one kept version in force: looking at what it would do first, then doing it. The same for a rollback. */
export class PolicyActivation {
    private readonly api: Api;
    private readonly purpose: Purpose;
    private readonly number: number;

    public constructor(api: Api, purpose: Purpose, number: number) {
        this.api = api;
        this.purpose = purpose;
        this.number = number;
    }

    public async preview(): Promise<PolicyConsequences> {
        return new PolicyConsequences(
            await this.api.get("/policy-previews", { query: { purpose: this.purpose, number: this.number } }),
        );
    }

    public async confirm(comment: string): Promise<void> {
        await this.api.post("/policy-activations", { purpose: this.purpose, number: this.number, comment });
    }
}
