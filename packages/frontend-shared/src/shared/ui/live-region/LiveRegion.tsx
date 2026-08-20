import type { ReactNode } from "react";
import { VisuallyHidden } from "../visually-hidden";

export type LiveRegionPoliteness = "polite" | "assertive";

export interface LiveRegionProps {
    /** @default "polite" */
    readonly politeness?: LiveRegionPoliteness;
    /** The announcement. Re-render with new text to trigger a fresh announcement — never unmount and remount this component to do so, see below. */
    readonly children: ReactNode;
    readonly className?: string;
}

/**
 * `role="status"`/`role="alert"` alongside the matching `aria-live` value is
 * deliberate belt-and-suspenders, not redundancy: the two mechanisms have
 * inconsistent support across real assistive-tech/browser pairings, and
 * setting both is the standard WAI-ARIA Authoring Practices recommendation
 * for exactly this reason. `aria-atomic="true"` on both so a screen reader
 * reads the whole new message rather than trying to diff it against the old
 * one word by word.
 */
const POLITENESS_ROLE: Record<LiveRegionPoliteness, "status" | "alert"> = {
    polite: "status",
    assertive: "alert",
};

/**
 * Tier 0 — an always-mounted, visually hidden announcement channel for the
 * status updates `Toast` does not cover: a filtered result count, a
 * background save finishing. Composes `VisuallyHidden` directly (Tier 0
 * composing Tier 0, `COMPONENTS.md` §2) rather than reimplementing the
 * clipping technique.
 *
 * Must stay mounted for the caller's whole lifetime and receive new
 * `children` in place. Most assistive tech only picks up an `aria-live`
 * region that was already present in the accessibility tree *before* its
 * content changed — mounting a fresh `LiveRegion` with the message already
 * inside it, or unmounting between announcements, is the standard way this
 * pattern silently fails to announce anything at all.
 */
export function LiveRegion({ politeness = "polite", children, className }: LiveRegionProps) {
    return (
        <VisuallyHidden
            render={ <div aria-live={ politeness } aria-atomic="true" role={ POLITENESS_ROLE[politeness] }/> }
            { ...(className ? { className } : {}) }
        >
            { children }
        </VisuallyHidden>
    );
}
