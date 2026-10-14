import { useStrings } from "@/shared/i18n";
import type { SpanUnit, SpanWords } from "./Span";

export interface SpanTexts {
    /** "7 d", "30 s": a number with its unit, for a line of text. */
    readonly words: SpanWords;
    /** "days", "seconds": the unit on its own, for the choice beside a field. */
    readonly unitLabels: Readonly<Record<SpanUnit, string>>;
    readonly unitGroupLabel: string;
}

/** The words a span is written and chosen in, from the app's dictionary. */
export function useSpanWords(): SpanTexts {
    const t = useStrings("span");
    return {
        words: {
            seconds: (amount) => t("seconds", { amount }),
            minutes: (amount) => t("minutes", { amount }),
            hours: (amount) => t("hours", { amount }),
            days: (amount) => t("days", { amount }),
        },
        unitLabels: { seconds: t("unitSeconds"), minutes: t("unitMinutes"), hours: t("unitHours"), days: t("unitDays") },
        unitGroupLabel: t("unitGroup"),
    };
}
