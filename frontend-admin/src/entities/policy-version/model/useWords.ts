import { useStrings } from "@/shared/i18n";
import { formatCount } from "@/shared/number";
import { useSpanWords } from "@/shared/span";
import type { PolicyConsequenceWords } from "./PolicyConsequences";
import { PolicyFaults } from "./PolicyFaults";
import type { PolicyWords } from "./PolicyForm";

/** The words a policy is summarised in: its factors, whom each step is for, and its limits. */
export function usePolicyWords(): PolicyWords {
    const factors = useStrings("factors");
    const necessity = useStrings("necessity");
    const t = useStrings("policyWords");
    const { words } = useSpanWords();
    return {
        factor: { google: factors("google"), totp: factors("totp"), recovery_code: factors("recovery_code") },
        necessity: { always: necessity("always"), when_enrolled: necessity("when_enrolled") },
        or: t("or"),
        then: t("then"),
        step: (factorNames, who) => t("step", { factors: factorNames, who }),
        limits: (lifetime, failures, delay) => t("limits", { lifetime, failures, delay }),
        span: words,
        stepsSection: t("stepsSection"),
        limitsSection: t("limitsSection"),
        stepAt: (position, step) => t("stepAt", { position, step }),
        lifetimeLine: (lifetime) => t("lifetimeLine", { lifetime }),
        failuresLine: (failures) => t("failuresLine", { failures }),
        delayLine: (delay) => t("delayLine", { delay }),
    };
}

export function usePolicyConsequenceWords(): PolicyConsequenceWords {
    const t = useStrings("policyWords");
    return {
        people: (count) => t("people", { count: formatCount(count) }),
        cannotPass: (count) => t("cannotPass", { count: formatCount(count) }),
    };
}

/** What the server's faults about a policy are called, from the app's dictionary. */
export function usePolicyFaults(): PolicyFaults {
    const t = useStrings("faults");
    return new PolicyFaults({
        unknown: t("unknown"),
        stepEmpty: (position) => t("stepEmpty", { position }),
        nothingIdentifies: t("nothingIdentifies"),
        belowTheFloor: t("belowTheFloor"),
        wouldLockPeopleOut: t("wouldLockPeopleOut"),
        outOfBounds: (limit) =>
            t(limit === "attemptLifetime" ? "outOfBoundsAttemptLifetime" : limit === "maxFailures" ? "outOfBoundsMaxFailures" : "outOfBoundsFirstDelay"),
        commentInvalid: t("commentInvalid"),
        unmapped: (field, code) => t("unmapped", { field, code }),
    });
}
