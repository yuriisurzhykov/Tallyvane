import type { Api } from "frontend-shared/api";
import type { PolicyForm, Purpose } from "../model/PolicyForm";
import { PolicyHistory } from "../model/PolicyHistory";
import { PolicyVersion } from "../model/PolicyVersion";
import { PolicyActivation } from "./PolicyActivation";

/** The sign-in policy versions on the server, and what can be done to them. Administrators' site only. */
export class PolicyVersions {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    public async history(purpose: Purpose): Promise<PolicyHistory> {
        const { versions } = await this.api.get("/policy-history", { query: { purpose } });
        return new PolicyHistory(versions.map((wire) => new PolicyVersion(wire)));
    }

    /** Keeps the form as the next version of the purpose and says its number. Nothing is in force that was not before. A refusal throws; see `Rejection`. */
    public async add(purpose: Purpose, form: PolicyForm, comment: string): Promise<number> {
        const made = await this.api.post("/policy-versions", form.toRequest(purpose, comment));
        return made.number;
    }

    public activation(purpose: Purpose, number: number): PolicyActivation {
        return new PolicyActivation(this.api, purpose, number);
    }
}
