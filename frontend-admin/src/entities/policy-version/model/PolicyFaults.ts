import type { Fault } from "frontend-shared/api";

export interface PolicyFaultWords {
    readonly unknown: string;
    /** A step with nothing ticked; `position` counts from 1, as the screen numbers its steps. */
    readonly stepEmpty: (position: number) => string;
    readonly nothingIdentifies: string;
    readonly belowTheFloor: string;
    readonly wouldLockPeopleOut: string;
    readonly outOfBounds: (limit: PolicyLimit) => string;
    readonly commentInvalid: string;
    readonly unmapped: (field: string, code: string) => string;
}

/** The limits that have a field of their own on the form. */
export type PolicyLimit = "attemptLifetime" | "maxFailures" | "firstDelay";

const LIMIT_OF_FIELD: Readonly<Record<string, PolicyLimit>> = {
    attempt_lifetime_millis: "attemptLifetime",
    max_failures: "maxFailures",
    first_delay_millis: "firstDelay",
};

const STEP_ACCEPTS = /^steps\[(\d+)\]\.accepts$/;

/** The fields of a policy that show their own error beside them; everything else is listed under the form. */
export const POLICY_LIMIT_FIELDS: readonly string[] = Object.keys(LIMIT_OF_FIELD);

/** What the server's faults about a policy are called in the person's words. */
export class PolicyFaults {
    private readonly words: PolicyFaultWords;

    public constructor(words: PolicyFaultWords) {
        this.words = words;
    }

    public phrase(fault: Fault): string {
        const step = STEP_ACCEPTS.exec(fault.field);
        if (step?.[1] !== undefined && fault.code === "empty") {
            return this.words.stepEmpty(Number(step[1]) + 1);
        }
        const limit = LIMIT_OF_FIELD[fault.field];
        if (limit !== undefined && fault.code === "out-of-bounds") {
            return this.words.outOfBounds(limit);
        }
        if (fault.field === "steps") {
            switch (fault.code) {
                case "nothing-identifies-the-account":
                    return this.words.nothingIdentifies;
                case "below-the-floor":
                    return this.words.belowTheFloor;
                case "would-lock-people-out":
                    return this.words.wouldLockPeopleOut;
            }
        }
        if (fault.field === "comment" && fault.code === "comment-invalid") {
            return this.words.commentInvalid;
        }
        if (fault.field === "purpose" && fault.code === "unknown") {
            return this.words.unknown;
        }
        return this.words.unmapped(fault.field, fault.code);
    }
}
