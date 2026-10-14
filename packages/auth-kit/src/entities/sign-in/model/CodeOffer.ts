import type { CodeKind } from "./CodeKind";

/** What a waiting sign-in asks for: which kinds of code it takes, and how much longer it will not look at one. */
export class CodeOffer {
    private readonly kinds: readonly CodeKind[];
    private readonly pauseSeconds: number;

    public constructor(kinds: readonly CodeKind[], pauseSeconds: number) {
        this.kinds = kinds;
        this.pauseSeconds = pauseSeconds;
    }

    public offers(kind: CodeKind): boolean {
        return this.kinds.includes(kind);
    }

    /** The kind to show first: the code from the app when it is accepted, the recovery code when it is all there is. */
    public firstKind(): CodeKind {
        return this.offers("totp") ? "totp" : "recovery_code";
    }

    /** Whole seconds still to wait, `0` when none. */
    public pause(): number {
        return this.pauseSeconds;
    }

    /** `true` when there is something to type at all. */
    public isEmpty(): boolean {
        return this.kinds.length === 0;
    }
}
