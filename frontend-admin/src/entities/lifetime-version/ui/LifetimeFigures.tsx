import { Row } from "frontend-shared/ui/row";
import { Stat } from "frontend-shared/ui/stat";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import { useSpanWords } from "@/shared/span";
import type { LifetimeForm } from "../model/LifetimeForm";

export interface LifetimeFiguresProps {
    readonly form: LifetimeForm;
    /** Smaller figures, for a place that shows the lengths as a consequence of what is being edited. */
    readonly compact?: boolean;
}

/** The three lengths of a session as three large figures, each under the name the editor gives it. */
export function LifetimeFigures({ form, compact = false }: LifetimeFiguresProps) {
    const t = useStrings("lifetimeEditor");
    const { words } = useSpanWords();
    const figure = (label: string, said: string) => (
        <Stat label={label} value={<Text variant={compact ? "title3" : "title1"}>{said}</Text>} />
    );

    return (
        <Row gap="section-gap" className="flex-wrap items-start">
            {figure(t("idle"), form.idleSpan().describe(words))}
            {figure(t("absolute"), form.absoluteSpan().describe(words))}
            {figure(t("freshness"), form.freshnessSpan().describe(words))}
        </Row>
    );
}
