export type SpanUnit = "seconds" | "minutes" | "hours" | "days";

const UNIT_MS: Readonly<Record<SpanUnit, number>> = {
    seconds: 1_000,
    minutes: 60_000,
    hours: 3_600_000,
    days: 86_400_000,
};

/** Largest first, so the first unit that divides a span evenly is the one a person would say it in. */
const LARGEST_FIRST: readonly SpanUnit[] = ["days", "hours", "minutes", "seconds"];

/** How each unit is written with a number in front of it, in the words of whoever shows the span. */
export type SpanWords = Readonly<Record<SpanUnit, (amount: number) => string>>;

/**
 * A length of time. The server counts it in milliseconds; a person thinks in "7 days" or "30 seconds",
 * so the number and the unit meet here and nowhere else.
 */
export class Span {
    private readonly millis: number;

    private constructor(millis: number) {
        this.millis = millis;
    }

    public static ofMillis(millis: number): Span {
        return new Span(Math.round(millis));
    }

    public static of(amount: number, unit: SpanUnit): Span {
        return new Span(Math.round(amount * UNIT_MS[unit]));
    }

    public toMillis(): number {
        return this.millis;
    }

    public amountIn(unit: SpanUnit): number {
        return this.millis / UNIT_MS[unit];
    }

    /**
     * The unit this span is most naturally said in, among the ones on offer: the largest that divides it
     * evenly, or the smallest on offer when none does (the amount is then a fraction of that unit).
     */
    public naturalUnit(offered: readonly SpanUnit[] = LARGEST_FIRST): SpanUnit {
        const candidates = LARGEST_FIRST.filter((unit) => offered.includes(unit));
        const fitting = candidates.find((unit) => this.millis !== 0 && this.millis % UNIT_MS[unit] === 0);
        const smallest = candidates[candidates.length - 1];
        if (fitting !== undefined) {
            return fitting;
        }
        if (smallest === undefined) {
            throw new Error("A span needs at least one unit to be said in.");
        }
        return smallest;
    }

    public describe(words: SpanWords): string {
        const unit = this.naturalUnit();
        return words[unit](this.amountIn(unit));
    }

    public equals(other: Span): boolean {
        return this.millis === other.millis;
    }
}
