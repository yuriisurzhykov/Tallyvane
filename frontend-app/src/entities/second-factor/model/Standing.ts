import type { components } from "frontend-shared/api";

type Wire = components["schemas"]["SecondFactorState"];

/**
 * What the signed-in person has set up as a second factor: nothing (or a set-up begun and not confirmed),
 * an authenticator that works, or one retired because a recovery code was spent, so that only the
 * remaining recovery codes work until it is set up again (ADR-093).
 */
export class Standing {
    private readonly wire: Wire;

    public constructor(wire: Wire) {
        this.wire = wire;
    }

    public isOff(): boolean {
        return this.wire.standing === "off";
    }

    public isActive(): boolean {
        return this.wire.standing === "active";
    }

    public isRetired(): boolean {
        return this.wire.standing === "retired";
    }

    /** Recovery codes still unspent. */
    public codesLeft(): number {
        return this.wire.recovery_codes_remaining;
    }
}
