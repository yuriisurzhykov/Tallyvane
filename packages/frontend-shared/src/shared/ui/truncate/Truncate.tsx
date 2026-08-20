import type { ReactNode } from "react";

export interface TruncateProps {
    readonly children: ReactNode;
    /** @default 1 */
    readonly lines?: number;
    /** The untruncated value — set as the native `title` attribute so it stays reachable. */
    readonly fullValue?: string;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * Tier 0 — line clamp with the full value reachable.
 *
 * Does not compose `Text`: unlike `Numeric`, this wraps children of any
 * typography, so it must not fix a variant of its own.
 *
 * `lines` drives an inline `WebkitLineClamp`, paired with the `display` and
 * `WebkitBoxOrient` the clamp mechanism needs to function — like `Grid`'s
 * `columns`, no token names an arbitrary line count, so this is the one
 * acceptable inline style here.
 *
 * The full value is exposed through the native `title` attribute when
 * `fullValue` is given — a real, always-available, zero-dependency fallback.
 * `Tooltip` does not exist yet in this package, and reaching for one here
 * would be exactly the kind of one-off dependency `COMPONENTS.md` warns
 * against. When `fullValue` is omitted, only the visually clamped content is
 * reachable; this component does not attempt to extract text from `children`
 * to synthesize a fallback, since `children` may not be a plain string.
 */
export function Truncate({ children, lines = 1, fullValue, className }: TruncateProps) {
    return (
        <div
            className={["overflow-hidden", className].filter(Boolean).join(" ")}
            style={{
                display: "-webkit-box",
                WebkitBoxOrient: "vertical",
                WebkitLineClamp: lines,
            }}
            {...(fullValue ? { title: fullValue } : {})}
        >
            {children}
        </div>
    );
}
