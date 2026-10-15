import { describe, expect, it } from "vitest";
import { Span, type SpanWords } from "@/shared/span";
import type { DiffLook } from "@/shared/diff";
import { PolicyForm, type PolicyWords } from "./PolicyForm";
import { PolicyStep } from "./PolicyStep";

const spanWords: SpanWords = {
    seconds: (n) => `${String(n)} s`,
    minutes: (n) => `${String(n)} min`,
    hours: (n) => `${String(n)} h`,
    days: (n) => `${String(n)} d`,
};

const words: PolicyWords = {
    factor: { google: "Google", totp: "App", recovery_code: "Recovery" },
    necessity: { always: "everyone", when_enrolled: "if set up" },
    or: " or ",
    step: (factors, who) => `${factors} (${who})`,
    then: " > ",
    limits: (lifetime, failures, delay) => `${lifetime}, ${String(failures)} failures, ${delay}`,
    span: spanWords,
    stepsSection: "steps",
    limitsSection: "limits",
    stepAt: (position, step) => `step ${String(position)}: ${step}`,
    lifetimeLine: (lifetime) => `open for ${lifetime}`,
    failuresLine: (failures) => `${String(failures)} wrong`,
    delayLine: (delay) => `pause ${delay}`,
};

const form = () =>
    new PolicyForm(
        [
            PolicyStep.fromWire({ accepts: ["google"], necessity: "always" }),
            PolicyStep.fromWire({ accepts: ["totp", "recovery_code"], necessity: "when_enrolled" }),
        ],
        { attemptLifetime: Span.of(10, "minutes"), maxFailures: 5, firstDelay: Span.of(2, "seconds") },
    );

describe("PolicyForm", () => {
    it("says the whole policy in one line", () => {
        expect(form().summary(words)).toBe("Google (everyone) > App or Recovery (if set up). 10 min, 5 failures, 2 s");
    });

    it("sends what it holds, in the shape the server takes", () => {
        expect(form().toRequest("login", "Tighten")).toEqual({
            purpose: "login",
            steps: [
                { accepts: ["google"], necessity: "always" },
                { accepts: ["totp", "recovery_code"], necessity: "when_enrolled" },
            ],
            attempt_lifetime_millis: 600_000,
            max_failures: 5,
            first_delay_millis: 2_000,
            comment: "Tighten",
        });
    });

    it("leaves the form it was asked to change alone", () => {
        const before = form();

        before.withFactorToggled(0, "totp").withoutStep(1).withMaxFailures(9).withStepAdded();

        expect(before.toRequest("login", "x")).toEqual(form().toRequest("login", "x"));
    });

    it("ticks and unticks a factor of one step only", () => {
        const changed = form().withFactorToggled(0, "totp");

        expect(changed.toRequest("login", "x").steps[0]?.accepts).toEqual(["google", "totp"]);
        expect(changed.toRequest("login", "x").steps[1]?.accepts).toEqual(["totp", "recovery_code"]);
        expect(changed.withFactorToggled(0, "google").toRequest("login", "x").steps[0]?.accepts).toEqual(["totp"]);
    });

    it("keeps factors in their usual order whatever order they were ticked in", () => {
        const changed = new PolicyForm([PolicyStep.fromWire({ accepts: ["recovery_code"], necessity: "always" })], { attemptLifetime: Span.of(1, "minutes"), maxFailures: 1, firstDelay: Span.of(1, "seconds") })
            .withFactorToggled(0, "google");

        expect(changed.toRequest("login", "x").steps[0]?.accepts).toEqual(["google", "recovery_code"]);
    });

    it("changes whom a step applies to", () => {
        const changed = form().withNecessity(0, "when_enrolled");

        expect(changed.toRequest("login", "x").steps[0]?.necessity).toBe("when_enrolled");
    });

    it("adds a second-factor step for those who have one, and removes a step by position", () => {
        const added = form().withStepAdded();

        expect(added.stepCount()).toBe(3);
        expect(added.toRequest("login", "x").steps[2]).toEqual({ accepts: ["totp"], necessity: "when_enrolled" });
        expect(added.withoutStep(0).stepCount()).toBe(2);
        expect(added.withoutStep(0).toRequest("login", "x").steps[0]?.accepts).toEqual(["totp", "recovery_code"]);
    });

    it("changes the limits", () => {
        const changed = form().withAttemptLifetime(Span.of(1, "hours")).withMaxFailures(3).withFirstDelay(Span.of(30, "seconds"));

        expect(changed.toRequest("login", "x")).toMatchObject({
            attempt_lifetime_millis: 3_600_000,
            max_failures: 3,
            first_delay_millis: 30_000,
        });
    });

    it("knows a step that accepts nothing", () => {
        const emptied = PolicyStep.fromWire({ accepts: ["google"], necessity: "always" }).withFactorToggled("google");

        expect(emptied.acceptsNothing()).toBe(true);
    });

    it("says the limits in one line of their own", () => {
        expect(form().limitsLine(words)).toBe("10 min, 5 failures, 2 s");
    });
});

describe("PolicyForm.changesFrom", () => {
    const look: DiffLook<string> = { section: (t) => `# ${t}`, removed: (t) => `- ${t}`, added: (t) => `+ ${t}` };
    const told = (now: PolicyForm, before: PolicyForm) => now.changesFrom(before, words).map((line) => line.appearAs(look));

    it("has nothing to tell about a policy and its copy", () => {
        expect(form().changesFrom(form(), words).isEmpty()).toBe(true);
    });

    it("tells each limit that changed, what it was and what it is, and no other", () => {
        const now = form().withMaxFailures(3).withFirstDelay(Span.of(10, "seconds"));

        expect(told(now, form())).toEqual(["# limits", "- 5 wrong", "+ 3 wrong", "- pause 2 s", "+ pause 10 s"]);
    });

    it("tells a step whose factors changed, in its position", () => {
        const now = form().withFactorToggled(0, "totp");

        expect(told(now, form())).toEqual(["# steps", "- step 1: Google (everyone)", "+ step 1: Google or App (everyone)"]);
    });

    it("tells a step whose audience changed", () => {
        const now = form().withNecessity(1, "always");

        expect(told(now, form())).toEqual([
            "# steps",
            "- step 2: App or Recovery (if set up)",
            "+ step 2: App or Recovery (everyone)",
        ]);
    });

    it("tells a step that was added and a step that was removed", () => {
        expect(told(form().withStepAdded(), form())).toEqual(["# steps", "+ step 3: App (if set up)"]);
        expect(told(form().withoutStep(1), form())).toEqual(["# steps", "- step 2: App or Recovery (if set up)"]);
    });
});
