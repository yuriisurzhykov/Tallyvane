"use client";

import type { ReactNode } from "react";
import { ScrollArea as BaseScrollArea } from "@base-ui/react/scroll-area";

export interface ScrollAreaProps {
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. Applied to the scroll area's root. */
    readonly className?: string;
}

/**
 * Thickness is `--ds-component-scroll-area-thickness`. Named constants are
 * no longer a lint exemption.
 *
 * Tier 0 — a scroll container with styled bars, so an inner scroll region
 * never looks like a bare browser default. One visual treatment; no variant
 * prop, per the brief this was built against.
 */
export function ScrollArea({ children, className }: ScrollAreaProps) {
    return (
        <BaseScrollArea.Root className={["relative h-full w-full overflow-hidden", className].filter(Boolean).join(" ")}>
            <BaseScrollArea.Viewport className="h-full w-full" data-testid="scroll-area-viewport">
                {children}
            </BaseScrollArea.Viewport>
            <BaseScrollArea.Scrollbar orientation="vertical" className="bg-surface-inset" style={{ width: "var(--ds-component-scroll-area-thickness)" }}>
                <BaseScrollArea.Thumb className="w-full rounded-pill bg-border-strong" />
            </BaseScrollArea.Scrollbar>
            <BaseScrollArea.Scrollbar orientation="horizontal" className="bg-surface-inset" style={{ height: "var(--ds-component-scroll-area-thickness)" }}>
                <BaseScrollArea.Thumb className="h-full rounded-pill bg-border-strong" />
            </BaseScrollArea.Scrollbar>
            <BaseScrollArea.Corner className="bg-surface-inset" />
        </BaseScrollArea.Root>
    );
}
