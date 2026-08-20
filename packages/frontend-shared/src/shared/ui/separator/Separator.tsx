import { Separator as BaseSeparator } from "@base-ui/react/separator";

export interface SeparatorProps {
    /** @default "horizontal" */
    readonly orientation?: "horizontal" | "vertical";
    /**
     * Hides the separator from the accessibility tree (`role="none"`) for a
     * purely visual hairline that separates nothing semantically meaningful.
     *
     * The installed `@base-ui/react` (1.7.0) ships no `decorative` prop on its
     * `Separator` — verified against `Separator.d.ts` and the upstream docs,
     * which list only `orientation`, `className`, `style` and `render`. Radix
     * had this prop; Base UI's stance is that decorative-vs-semantic is an ARIA
     * concern the caller expresses directly. This prop restores the same
     * caller-facing API by translating to `role="none"` ourselves, which Base
     * UI's own prop-merge order lets an explicit `role` override cleanly.
     * @default false
     */
    readonly decorative?: boolean;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/** Tier 0 — a hairline, styled from the border-subtle role. Behaviour is Base UI's. */
export function Separator({ orientation = "horizontal", decorative = false, className }: SeparatorProps) {
    return (
        <BaseSeparator
            orientation={orientation}
            {...(decorative ? { role: "none" as const } : {})}
            className={[
                orientation === "horizontal" ? "w-full border-t border-border-subtle" : "h-full border-l border-border-subtle",
                className,
            ]
                .filter(Boolean)
                .join(" ")}
        />
    );
}
