import type { components } from "frontend-shared/api";

type Wire = components["schemas"]["PolicyPreview"];

export interface PolicyConsequenceWords {
    readonly people: (count: number) => string;
    readonly cannotPass: (count: number) => string;
}

/** What putting a version in force would do to the people it applies to. */
export class PolicyConsequences {
    private readonly wire: Wire;

    public constructor(wire: Wire) {
        this.wire = wire;
    }

    /** Nobody would be left unable to finish a sign-in. */
    public isHarmless(): boolean {
        return this.wire.cannot_pass === 0;
    }

    public describe(words: PolicyConsequenceWords): readonly string[] {
        const lines = [words.people(this.wire.people)];
        return this.isHarmless() ? lines : [...lines, words.cannotPass(this.wire.cannot_pass)];
    }
}
