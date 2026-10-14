import type { components } from "frontend-shared/api";

type Wire = components["schemas"]["PolicyStep"];

export type Factor = Wire["accepts"][number];
export type Necessity = Wire["necessity"];

/** In the order a person meets them, which is also the order the screen lists them in. */
export const FACTORS: readonly Factor[] = ["google", "totp", "recovery_code"];
export const NECESSITIES: readonly Necessity[] = ["always", "when_enrolled"];

/** The words that name a factor and a necessity, as a summary line or a form label says them. */
export interface StepWords {
    readonly factor: Readonly<Record<Factor, string>>;
    readonly necessity: Readonly<Record<Necessity, string>>;
    /** Between two factors a step accepts. */
    readonly or: string;
    readonly step: (factors: string, who: string) => string;
}

/**
 * One step of a sign-in: satisfied by any one of the factors it accepts, and asked of everyone or only of
 * those who have set a factor up. A step is a value; every change gives a new one.
 */
export class PolicyStep {
    private readonly factors: readonly Factor[];
    private readonly necessity: Necessity;

    private constructor(factors: readonly Factor[], necessity: Necessity) {
        this.factors = FACTORS.filter((factor) => factors.includes(factor));
        this.necessity = necessity;
    }

    public static fromWire(wire: Wire): PolicyStep {
        return new PolicyStep(wire.accepts, wire.necessity);
    }

    /** What a step added with "Add a step" starts as: a second factor, for those who have one. */
    public static added(): PolicyStep {
        return new PolicyStep(["totp"], "when_enrolled");
    }

    public accepts(factor: Factor): boolean {
        return this.factors.includes(factor);
    }

    public acceptsNothing(): boolean {
        return this.factors.length === 0;
    }

    public appliesTo(necessity: Necessity): boolean {
        return this.necessity === necessity;
    }

    public withFactorToggled(factor: Factor): PolicyStep {
        const factors = this.accepts(factor) ? this.factors.filter((kept) => kept !== factor) : [...this.factors, factor];
        return new PolicyStep(factors, this.necessity);
    }

    public withNecessity(necessity: Necessity): PolicyStep {
        return new PolicyStep(this.factors, necessity);
    }

    public describe(words: StepWords): string {
        const factors = this.factors.map((factor) => words.factor[factor]).join(words.or);
        return words.step(factors, words.necessity[this.necessity]);
    }

    public toRequest(): Wire {
        return { accepts: [...this.factors], necessity: this.necessity };
    }
}
