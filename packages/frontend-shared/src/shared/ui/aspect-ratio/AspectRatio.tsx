import type { ReactNode } from "react";

export interface AspectRatioProps {
    /** Width divided by height, e.g. `16 / 9`. */
    readonly ratio: number;
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * Tier 0 — a media box that does not shift on load.
 *
 * `@base-ui/react` 1.7.0 ships no `AspectRatio` primitive — verified against
 * the installed package's export map, which has no `aspect-ratio` entry.
 * There is nothing behavioural to delegate here anyway (no focus management,
 * no keyboard path): the CSS `aspect-ratio` property alone reserves the box's
 * space before its content loads, so this is a genuinely minimal, self-built
 * substitute rather than a hand-rolled reimplementation of something Base UI
 * already solves elsewhere.
 */
export function AspectRatio({ ratio, children, className }: AspectRatioProps) {
    return (
        <div
            className={["relative w-full overflow-hidden", className].filter(Boolean).join(" ")}
            style={{ aspectRatio: String(ratio) }}
        >
            {children}
        </div>
    );
}
