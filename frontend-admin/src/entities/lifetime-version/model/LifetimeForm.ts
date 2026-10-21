import type { components } from "frontend-shared/api";
import { Diff, DiffLine } from "@/shared/diff";
import type { Span, SpanWords } from "@/shared/span";

type Proposal = components["schemas"]["LifetimeProposal"];
export type Client = Proposal["client"];

/** The clients in the order the screen offers them. */
export const CLIENTS: readonly Client[] = ["browser", "admin"];

export interface LifetimeWords {
    readonly span: SpanWords;
    readonly summary: (idle: string, absolute: string, freshness: string) => string;
    /** One length in a list of differences, said as the screen names it elsewhere: "Unused for 1 d". */
    readonly idleLine: (idle: string) => string;
    readonly absoluteLine: (absolute: string) => string;
    readonly freshnessLine: (freshness: string) => string;
}

/** The three lengths of a session, being edited to be kept as the next version. Every edit gives a new form. */
export class LifetimeForm {
    private readonly idle: Span;
    private readonly absolute: Span;
    private readonly freshness: Span;

    public constructor(idle: Span, absolute: Span, freshness: Span) {
        this.idle = idle;
        this.absolute = absolute;
        this.freshness = freshness;
    }

    public idleSpan(): Span {
        return this.idle;
    }

    public absoluteSpan(): Span {
        return this.absolute;
    }

    public freshnessSpan(): Span {
        return this.freshness;
    }

    public withIdle(idle: Span): LifetimeForm {
        return new LifetimeForm(idle, this.absolute, this.freshness);
    }

    public withAbsolute(absolute: Span): LifetimeForm {
        return new LifetimeForm(this.idle, absolute, this.freshness);
    }

    public withFreshness(freshness: Span): LifetimeForm {
        return new LifetimeForm(this.idle, this.absolute, freshness);
    }

    public summary(words: LifetimeWords): string {
        return words.summary(this.idle.describe(words.span), this.absolute.describe(words.span), this.freshness.describe(words.span));
    }

    /** How these lengths differ from `before`: what `before` had that this does not (removed) and what this has that `before` did not (added). */
    public changesFrom(before: LifetimeForm, words: LifetimeWords): Diff {
        const lines: DiffLine[] = [];
        const compare = (was: Span, now: Span, say: (length: string) => string) => {
            if (was.equals(now)) return;
            lines.push(DiffLine.removed(say(was.describe(words.span))), DiffLine.added(say(now.describe(words.span))));
        };
        compare(before.idle, this.idle, words.idleLine);
        compare(before.absolute, this.absolute, words.absoluteLine);
        compare(before.freshness, this.freshness, words.freshnessLine);
        return Diff.of(lines);
    }

    public toRequest(client: Client, comment: string): Proposal {
        return {
            client,
            idle_millis: this.idle.toMillis(),
            absolute_millis: this.absolute.toMillis(),
            freshness_millis: this.freshness.toMillis(),
            comment,
        };
    }
}
