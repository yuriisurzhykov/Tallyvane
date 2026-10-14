import type { Fault } from "frontend-shared/api";

/** The lengths that have a field of their own on the form. */
export type LifetimeLimit = "idle" | "absolute" | "freshness";

export interface LifetimeFaultWords {
    readonly unknown: string;
    readonly outOfBounds: (limit: LifetimeLimit) => string;
    readonly belowIdle: string;
    readonly commentInvalid: string;
    readonly unmapped: (field: string, code: string) => string;
}

const LIMIT_OF_FIELD: Readonly<Record<string, LifetimeLimit>> = {
    idle_millis: "idle",
    absolute_millis: "absolute",
    freshness_millis: "freshness",
};

/** The fields of the lifetimes that show their own error beside them. */
export const LIFETIME_LIMIT_FIELDS: readonly string[] = Object.keys(LIMIT_OF_FIELD);

/** What the server's faults about session lifetimes are called in the person's words. */
export class LifetimeFaults {
    private readonly words: LifetimeFaultWords;

    public constructor(words: LifetimeFaultWords) {
        this.words = words;
    }

    public phrase(fault: Fault): string {
        const limit = LIMIT_OF_FIELD[fault.field];
        if (limit !== undefined && fault.code === "out-of-bounds") {
            return this.words.outOfBounds(limit);
        }
        if (fault.field === "absolute_millis" && fault.code === "below-idle") {
            return this.words.belowIdle;
        }
        if (fault.field === "comment" && fault.code === "comment-invalid") {
            return this.words.commentInvalid;
        }
        if (fault.field === "client" && fault.code === "unknown") {
            return this.words.unknown;
        }
        return this.words.unmapped(fault.field, fault.code);
    }
}
