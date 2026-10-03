import { ProblemError, type Api } from "frontend-shared/api";
import { Confirmation } from "../model/Confirmation";
import { RecoveryCodes } from "../model/RecoveryCodes";
import { Standing } from "../model/Standing";
import { TotpKey } from "../model/TotpKey";

/**
 * The signed-in person's second factor. Beginning, turning off and reissuing are dangerous acts: when the
 * last proof of who they are has gone stale the chain asks for a new one and repeats the call (ADR-092), so
 * these methods only see the answer, or a `StepUpDeclined` when the person said no.
 */
export class SecondFactors {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    public async standing(): Promise<Standing> {
        return new Standing(await this.api.get("/second-factor"));
    }

    /** A new key, told once. Beginning again starts over with another one. */
    public async begin(): Promise<TotpKey> {
        const started = await this.api.trigger("/totp-enrollments");
        return new TotpKey(started.key, started.uri);
    }

    /** Types the first code. Every call is a new intention, so it gets a key of its own: a retyped code is not a replay. */
    public async confirm(code: string): Promise<Confirmation> {
        try {
            const issued = await this.api.post("/totp-confirmations", { code });
            return Confirmation.confirmed(new RecoveryCodes(issued.recovery_codes));
        } catch (failure) {
            if (failure instanceof ProblemError && failure.isWrongCode()) {
                return Confirmation.wrong();
            }
            if (failure instanceof ProblemError && failure.hasStatus(409)) {
                return Confirmation.conflict();
            }
            throw failure;
        }
    }

    /** Turns TOTP off. Nothing left to turn off (`409`) is as off as the person wanted it, so that is not a failure. */
    public async disable(): Promise<void> {
        try {
            await this.api.delete("/totp-enrollment");
        } catch (failure) {
            if (!(failure instanceof ProblemError && failure.hasStatus(409))) {
                throw failure;
            }
        }
    }

    /** Ten new codes, told once, which replace every code the person had. */
    public async reissue(): Promise<RecoveryCodes> {
        const issued = await this.api.trigger("/recovery-codes");
        return new RecoveryCodes(issued.recovery_codes);
    }
}
