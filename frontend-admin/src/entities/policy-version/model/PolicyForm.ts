import type { components } from "frontend-shared/api";
import type { Span, SpanWords } from "@/shared/span";
import { PolicyStep, type Factor, type Necessity, type StepWords } from "./PolicyStep";

type Proposal = components["schemas"]["PolicyProposal"];
export type Purpose = Proposal["purpose"];

/** The purposes in the order the screen offers them. */
export const PURPOSES: readonly Purpose[] = ["registration", "login", "admin_login", "step_up"];

export interface PolicyWords extends StepWords {
    readonly span: SpanWords;
    /** Between two steps. */
    readonly then: string;
    readonly limits: (lifetime: string, failures: number, delay: string) => string;
}

/** The numbers beside the steps, which change together or not at all. */
export interface PolicyLimits {
    readonly attemptLifetime: Span;
    readonly maxFailures: number;
    readonly firstDelay: Span;
}

/**
 * A policy being edited: the steps and the limits of one version, to be kept as the next. A form is a value
 * too; every edit gives a new one, so a screen holding it can tell that something changed by looking.
 */
export class PolicyForm {
    private readonly steps: readonly PolicyStep[];
    private readonly limits: PolicyLimits;

    public constructor(steps: readonly PolicyStep[], limits: PolicyLimits) {
        this.steps = steps;
        this.limits = limits;
    }

    public stepCount(): number {
        return this.steps.length;
    }

    /** Shows every step in order, with its position (from 0). */
    public mapSteps<T>(show: (step: PolicyStep, position: number) => T): T[] {
        return this.steps.map(show);
    }

    public attemptLifetime(): Span {
        return this.limits.attemptLifetime;
    }

    public maxFailures(): number {
        return this.limits.maxFailures;
    }

    public firstDelay(): Span {
        return this.limits.firstDelay;
    }

    public withStepAdded(): PolicyForm {
        return new PolicyForm([...this.steps, PolicyStep.added()], this.limits);
    }

    public withoutStep(position: number): PolicyForm {
        return new PolicyForm(this.steps.filter((_step, at) => at !== position), this.limits);
    }

    public withFactorToggled(position: number, factor: Factor): PolicyForm {
        return this.withStepChanged(position, (step) => step.withFactorToggled(factor));
    }

    public withNecessity(position: number, necessity: Necessity): PolicyForm {
        return this.withStepChanged(position, (step) => step.withNecessity(necessity));
    }

    public withAttemptLifetime(attemptLifetime: Span): PolicyForm {
        return new PolicyForm(this.steps, { ...this.limits, attemptLifetime });
    }

    public withMaxFailures(maxFailures: number): PolicyForm {
        return new PolicyForm(this.steps, { ...this.limits, maxFailures });
    }

    public withFirstDelay(firstDelay: Span): PolicyForm {
        return new PolicyForm(this.steps, { ...this.limits, firstDelay });
    }

    /** One line saying the whole policy: "Google (Everyone) then Authenticator app (Only those who set it up), 10 min to finish, …". */
    public summary(words: PolicyWords): string {
        const steps = this.steps.map((step) => step.describe(words)).join(words.then);
        const limits = words.limits(
            this.limits.attemptLifetime.describe(words.span),
            this.limits.maxFailures,
            this.limits.firstDelay.describe(words.span),
        );
        return `${steps}. ${limits}`;
    }

    public toRequest(purpose: Purpose, comment: string): Proposal {
        return {
            purpose,
            steps: this.steps.map((step) => step.toRequest()),
            attempt_lifetime_millis: this.limits.attemptLifetime.toMillis(),
            max_failures: this.limits.maxFailures,
            first_delay_millis: this.limits.firstDelay.toMillis(),
            comment,
        };
    }

    private withStepChanged(position: number, change: (step: PolicyStep) => PolicyStep): PolicyForm {
        return new PolicyForm(this.steps.map((step, at) => (at === position ? change(step) : step)), this.limits);
    }
}
