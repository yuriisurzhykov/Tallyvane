import { useStrings } from "@/shared/i18n";
import { formatCount } from "@/shared/number";
import { useSpanWords } from "@/shared/span";
import type { LifetimeConsequenceWords } from "./LifetimeConsequences";
import { LifetimeFaults } from "./LifetimeFaults";
import type { LifetimeWords } from "./LifetimeForm";

/** The words a set of lifetimes is summarised in. */
export function useLifetimeWords(): LifetimeWords {
    const t = useStrings("lifetimeWords");
    const { words } = useSpanWords();
    return {
        span: words,
        summary: (idle, absolute, freshness) => t("summary", { idle, absolute, freshness }),
        idleLine: (idle) => t("idleLine", { idle }),
        absoluteLine: (absolute) => t("absoluteLine", { absolute }),
        freshnessLine: (freshness) => t("freshnessLine", { freshness }),
    };
}

export function useLifetimeConsequenceWords(): LifetimeConsequenceWords {
    const t = useStrings("lifetimeWords");
    return {
        sessions: (count) => t("sessions", { count: formatCount(count) }),
        wouldEnd: (count) => t("wouldEnd", { count: formatCount(count) }),
    };
}

/** What the server's faults about session lifetimes are called, from the app's dictionary. */
export function useLifetimeFaults(): LifetimeFaults {
    const t = useStrings("faults");
    return new LifetimeFaults({
        unknown: t("unknown"),
        outOfBounds: (limit) =>
            t(limit === "idle" ? "outOfBoundsIdle" : limit === "absolute" ? "outOfBoundsAbsolute" : "outOfBoundsFreshness"),
        belowIdle: t("belowIdle"),
        commentInvalid: t("commentInvalid"),
        unmapped: (field, code) => t("unmapped", { field, code }),
    });
}
