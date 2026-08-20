import type { ReactNode } from "react";
import { useRender } from "@base-ui/react/use-render";
import { mergeProps } from "@base-ui/react/merge-props";

export type IconButtonTone = "primary" | "neutral" | "ghost" | "danger";
export type IconButtonSize = "sm" | "md" | "lg";

/**
 * Square box, one size role driving both axes at once — the one respect in
 * which this differs from `Button`, whose width is content-driven and never
 * pinned to its height.
 */
const SIZE_CLASS: Record<IconButtonSize, string> = {
    sm: "h-(--control-height-sm) w-(--control-height-sm)",
    md: "h-(--control-height-md) w-(--control-height-md)",
    lg: "h-(--control-height-lg) w-(--control-height-lg)",
};

/**
 * `neutral` and `ghost` both sit flush with whatever surface they are placed
 * on at rest — no `surfacePrimary` fill of their own, so a `neutral` icon
 * button dropped onto `Surface variant="elevated"` or `"inset"` never shows a
 * mismatched patch of the page background. `border-border-default` is what
 * makes `neutral` read as bounded even so; `ghost` has neither border nor
 * fill until hovered. Both reuse `surface-row-hover` for that hover wash: it
 * is a themed overlay intensity, not a colour of its own (see
 * `contracts/color.ts`), so it sits correctly on either tone's transparent
 * base without a dedicated "hover" role for each.
 *
 * `danger` uses the solid `statusDanger` fill with `textOnSolid` on top —
 * the same "deep fill, light text" shape `primary` uses — rather than the
 * `-subtle`/`-text` pair `Badge` uses, precisely so it reads as a real,
 * clickable button and not a status badge that happens to be inside one.
 */
const TONE_CLASS: Record<IconButtonTone, string> = {
    primary: "bg-interactive-primary text-text-on-accent hover:bg-interactive-primary-hover active:bg-interactive-primary-pressed",
    neutral: "border border-border-default text-text-primary hover:bg-surface-row-hover",
    ghost: "text-text-primary hover:bg-surface-row-hover",
    danger: "bg-status-danger text-text-on-solid",
};

export interface IconButtonOwnProps {
    /** The accessible name. Required — an icon-only control with no name is not a valid button. */
    readonly label: string;
    /** No ambient default: a tone-less icon button is not a real option. */
    readonly tone: IconButtonTone;
    /** @default "md" */
    readonly size?: IconButtonSize;
    /**
     * The icon content. A generic slot, not typed against `Icon`'s own
     * (still-undecided) API — whatever renders here is responsible for its
     * own decorative-vs-meaningful accessibility treatment; `aria-label`
     * below already carries the button's name regardless of what `children`
     * does or does not announce on its own.
     */
    readonly children: ReactNode;
}

export type IconButtonProps = useRender.ComponentProps<"button"> & IconButtonOwnProps;

/** Icon-only action. Square at every size, and always named via `aria-label` — there is no icon-only button with no name. */
export function IconButton({ label, tone, size = "md", render, className, ...props }: IconButtonProps) {
    const classNames = `inline-flex items-center justify-center rounded-control outline-none transition-hover focus-visible:focus-ring ${SIZE_CLASS[size]} ${TONE_CLASS[tone]}`;

    return useRender({
        defaultTagName: "button",
        render,
        props: mergeProps<"button">(
            { className: classNames, type: "button" as const, "aria-label": label },
            { ...(className ? { className } : {}), ...props },
        ),
    });
}
