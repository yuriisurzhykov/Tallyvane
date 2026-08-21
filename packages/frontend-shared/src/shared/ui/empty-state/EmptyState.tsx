import type { ReactNode } from "react";
import { Stack } from "../stack";
import { Text } from "../text";

export interface EmptyStateProps {
    /**
     * A generic slot, not typed against `Icon`'s own (still-undecided) API —
     * the same reasoning `Button`'s `leadingIcon` and `Callout`'s
     * `leadingIcon` already use. Decorative: always rendered `aria-hidden`,
     * since the headline already carries the same meaning in words. Omit it
     * for an empty state with no icon.
     */
    readonly icon?: ReactNode;
    /** The headline — what is empty. */
    readonly title: string;
    /** The explanation — why, or what to do about it. Omit for a bare headline. */
    readonly description?: string;
    /** Typically a `Button`, e.g. "Add your first application" — accepts any `ReactNode` because the caller, not this component, decides what the one action actually is. Omit for a purely informational empty state. */
    readonly action?: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

const BASE_CLASS = "items-center py-section-gap text-center";

/**
 * Tier 1 — composes `Stack`/`Text` (Tier 0) for a centred column: icon,
 * headline, explanation, one action. Per `COMPONENTS.md` §4: "every list
 * needs one and they must not each invent it." `text-center` on the
 * outer `Stack` (an addition to its own `className`, not a new layout
 * primitive) centres every text child by inheritance — `Stack` itself
 * stays a plain vertical flow, so this reuses it rather than
 * reimplementing centred flex-column layout beside it.
 */
export function EmptyState({ icon, title, description, action, className }: EmptyStateProps) {
    return (
        <Stack gap="stack" className={[BASE_CLASS, className].filter(Boolean).join(" ")}>
            {icon ? <span aria-hidden="true">{icon}</span> : null}
            <Text variant="title3">{title}</Text>
            {description !== undefined ? (
                <Text variant="body" color="secondary">
                    {description}
                </Text>
            ) : null}
            {action}
        </Stack>
    );
}
