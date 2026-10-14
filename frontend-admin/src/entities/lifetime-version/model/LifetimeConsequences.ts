import type { components } from "frontend-shared/api";

type Wire = components["schemas"]["LifetimePreview"];

export interface LifetimeConsequenceWords {
    readonly sessions: (count: number) => string;
    readonly wouldEnd: (count: number) => string;
}

/** What putting a version in force would do to the sessions that are live now. */
export class LifetimeConsequences {
    private readonly wire: Wire;

    public constructor(wire: Wire) {
        this.wire = wire;
    }

    /** No live session would end. */
    public isHarmless(): boolean {
        return this.wire.would_end === 0;
    }

    public describe(words: LifetimeConsequenceWords): readonly string[] {
        const lines = [words.sessions(this.wire.sessions)];
        return this.isHarmless() ? lines : [...lines, words.wouldEnd(this.wire.would_end)];
    }
}
