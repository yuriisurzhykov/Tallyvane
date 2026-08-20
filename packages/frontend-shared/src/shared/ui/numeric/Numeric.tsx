import type { ReactNode } from "react";
import { Text } from "../text";

export interface NumericProps {
    readonly children: ReactNode;
    /** @default "right" */
    readonly align?: "left" | "right";
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

const ALIGN_CLASS: Record<"left" | "right", string> = {
    left: "text-left",
    right: "text-right",
};

/**
 * Tabular figures and slashed zero, right-aligned by default — the typography
 * for every salary, count and date in a column. `variant="numeric"` is fixed
 * on `Text`, not exposed as a prop: this component's entire purpose is being
 * numeric, so letting a caller pick a different `Text` variant here would
 * defeat it.
 *
 * This component does not format numbers. Turning an amount, date or count
 * into a display string is domain logic and belongs to a Tier 1+ component
 * (`Money`, `DateTime`) — `Numeric` only supplies the right typography and
 * alignment for whatever string the caller already formatted.
 */
export function Numeric({ children, align = "right", className }: NumericProps) {
    return (
        <Text variant="numeric" className={[ALIGN_CLASS[align], className].filter(Boolean).join(" ")}>
            {children}
        </Text>
    );
}
