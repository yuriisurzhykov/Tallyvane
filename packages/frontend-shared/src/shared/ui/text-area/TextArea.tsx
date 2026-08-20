import type { ComponentProps } from "react";

export interface TextAreaOwnProps {
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * No `size` prop, unlike `Input`: the `control` role is three FIXED height
 * steps for a single-line control, and a textarea's height is content-driven
 * by design — there is no small/medium/large for something that is supposed
 * to grow with what is typed into it.
 */
export type TextAreaProps = TextAreaOwnProps & Omit<ComponentProps<"textarea">, "className">;

/**
 * ~3 lines of `text-body` (1.625rem line-height) plus this component's own
 * vertical padding (`py-inline-tight`, 0.25rem each side) and its 1px border
 * — `3 × 1.625rem + 2 × 0.25rem + 2 × 1px = 5.5rem` exactly, under Tailwind's
 * preflight `box-sizing: border-box`. No spacing role names "how tall an
 * empty textarea starts", so this is a derived, one-off constant in the same
 * spirit as `Truncate`'s line-clamp count or `ScrollArea`'s scrollbar
 * thickness — referenced by identifier (not a literal in the JSX below) so
 * it stays exempt from `no-raw-dimension-value` for the same structural
 * reason `ScrollArea.tsx`'s `SCROLLBAR_THICKNESS` already is.
 */
const MIN_HEIGHT = "5.5rem";

const CLASS_NAME =
    "w-full field-sizing-content resize-y rounded-control border border-border-default bg-surface-inset px-inline py-inline-tight text-body text-text-primary placeholder:text-text-muted transition-hover focus-visible:focus-ring aria-invalid:border-status-danger disabled:cursor-not-allowed disabled:opacity-60";

/**
 * Tier 0 — auto-growing multi-line, per `COMPONENTS.md`. No Base UI (the
 * `Base` column is `—`): a plain `<textarea>` plus the native CSS
 * `field-sizing: content` property does the whole job with no
 * measurement/mirroring hack, and Base UI ships no textarea primitive to
 * reuse behaviour from regardless.
 *
 * `field-sizing: content` degrades safely where unsupported (Firefox and
 * Safari as of mid-2026): an unrecognised CSS property is simply ignored,
 * not cascade-blocking, leaving `min-height` and native `resize: vertical`
 * (`resize-y`) as the floor and the manual-resize affordance respectively.
 *
 * A plain `<textarea>` never gets Base UI's `data-disabled`/`data-invalid`
 * for free the way `Input` does — unlike `Input`'s `data-[disabled]:` (which
 * mirrors `Button`'s and `Toggle`'s own convention for a Base UI-backed
 * control), the disabled hook here is the native `disabled:` pseudo-class,
 * since nothing sets a `data-disabled` attribute on a plain `<textarea>`.
 * `aria-invalid` still works exactly like `Input`'s: a caller (or `Field`,
 * once it grows a textarea variant) sets it directly, no Base UI needed.
 */
export function TextArea({ className, style, ...props }: TextAreaProps) {
    return (
        <textarea
            className={[CLASS_NAME, className].filter(Boolean).join(" ")}
            style={{ minHeight: MIN_HEIGHT, ...style }}
            {...props}
        />
    );
}
