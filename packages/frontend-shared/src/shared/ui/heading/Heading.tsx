import type { ReactElement, ReactNode } from "react";
import { Text, type TextVariant } from "../text";

export type HeadingLevel = 1 | 2 | 3 | 4;

export interface HeadingProps {
    /** The place in the page's outline: one `1` per page, then `2` for its sections, and so on. */
    readonly level: HeadingLevel;
    /** How big it looks, chosen separately from the level because the outline and the design rarely agree. */
    readonly variant: TextVariant;
    readonly children: ReactNode;
    readonly className?: string;
}

const ELEMENT: Record<HeadingLevel, ReactElement> = {
    1: <h1 />,
    2: <h2 />,
    3: <h3 />,
    4: <h4 />,
};



/**
 * Tier 0 — a real heading. `Text` never picks a heading tag by itself, because a variant is a look and a
 * heading is a place in the outline; this is the one component that says "this is a heading" and at which
 * level, so screens outside `shared/ui` need no raw `<h1>`.
 */
export function Heading({ level, variant, children, className }: HeadingProps) {
    return (
        <Text variant={variant} render={ELEMENT[level]} {...(className !== undefined ? { className } : {})}>
            {children}
        </Text>
    );
}
