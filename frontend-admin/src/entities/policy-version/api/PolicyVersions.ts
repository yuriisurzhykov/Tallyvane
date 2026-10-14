import type { Api } from "frontend-shared/api";
import type { PolicyForm, Purpose } from "../model/PolicyForm";
import { PolicyHistory } from "../model/PolicyHistory";
import { PolicyVersion } from "../model/PolicyVersion";
import { IntentionKey } from "@/shared/idempotency";
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

    /**
     * Keeps the form as the next version of the purpose and says its number. Nothing is in force that was not before.
     * A refusal throws; see `Rejection`. Saying it again after an answer was lost does not keep a second version.
     */
    public async add(purpose: Purpose, form: PolicyForm, comment: string, intention: IntentionKey): Promise<number> {
        const request = form.toRequest(purpose, comment);
        const made = await intention.send(request, (idempotencyKey) =>
            this.api.post("/policy-versions", request, { idempotencyKey }),
        );
        return made.number;
    }

    public activation(purpose: Purpose, number: number): PolicyActivation {
        return new PolicyActivation(this.api, purpose, number, new IntentionKey());
    }
}
