import type { components } from "frontend-shared/api";
import { Span } from "@/shared/span";
import { VersionStamp } from "@/shared/history";
import { PolicyForm, type PolicyWords } from "./PolicyForm";
import { PolicyStep } from "./PolicyStep";

type Wire = components["schemas"]["PolicyVersion"];

/** One kept version of the sign-in policy of a purpose, as the server remembers it. */
export class PolicyVersion {
    private readonly wire: Wire;

    public constructor(wire: Wire) {
        this.wire = wire;
    }

    public number(): number {
        return this.wire.number;
    }

    /** The version that judges every sign-in of its purpose now. Exactly one of a history is. */
    public isInForce(): boolean {
        return this.wire.in_force !== undefined;
    }

    /** Who made it, when, and why. A version a migration made has no author and no reason. */
    public made(): VersionStamp {
        return new VersionStamp(this.wire.made_by, this.wire.made_at, this.wire.comment);
    }

    /** Who put it in force, when, and why; `undefined` for a version that is not in force. */
    public inForce(): VersionStamp | undefined {
        const { in_force: now } = this.wire;
        return now === undefined ? undefined : new VersionStamp(now.by, now.since, now.reason);
    }

    public summary(words: PolicyWords): string {
        return this.asForm().summary(words);
    }

    /** The form to start the next version from: this one, to be changed. */
    public asForm(): PolicyForm {
        return new PolicyForm(this.wire.steps.map((step) => PolicyStep.fromWire(step)), {
            attemptLifetime: Span.ofMillis(this.wire.attempt_lifetime_millis),
            maxFailures: this.wire.max_failures,
            firstDelay: Span.ofMillis(this.wire.first_delay_millis),
        });
    }
}
