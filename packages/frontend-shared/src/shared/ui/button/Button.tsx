import type { ReactNode } from "react";
import { Button as BaseButton } from "@base-ui/react/button";
import type { ButtonProps as BaseButtonProps } from "@base-ui/react/button";

export type ButtonTone = "primary" | "neutral" | "ghost" | "danger";
export type ButtonSize = "sm" | "md" | "lg";

export interface ButtonOwnProps {
    /** A button with no tone is unstyled by accident, not by choice — no ambient default, same reasoning as `Dot`'s `tone`. */
    readonly tone: ButtonTone;
    /** @default "md" */
    readonly size?: ButtonSize;
    /**
     * Disables the button and swaps the leading slot for an inline spinner,
     * with `aria-busy` announcing the state to assistive tech.
     * @default false
     */
    readonly loading?: boolean;
    readonly leadingIcon?: ReactNode;
    readonly trailingIcon?: ReactNode;
    /** The label. */
    readonly children: ReactNode;
    readonly className?: string;
}

/**
 * `@base-ui/react/button`'s own props, minus the fields this component
 * redeclares with a narrower type (`children` is required here; `className`
 * is a plain string, not Base UI's `string | ((state) => string)` form,
 * since no caller in this codebase needs the function form yet — YAGNI).
 */
export type ButtonProps = ButtonOwnProps & Omit<BaseButtonProps, "children" | "className">;

const BASE_CLASS =
    "inline-flex items-center justify-center gap-inline-tight rounded-control px-stack py-inline text-body-strong transition-hover focus-visible:focus-ring data-[disabled]:opacity-60 data-[disabled]:cursor-not-allowed";

/** `h-(--control-height-*)`, per `semantic/control.ts` — the adapter exposes these as CSS variables, not generated `h-sm`/`h-md`/`h-lg` utilities, so a control reads its own height through this arbitrary-value form rather than a raw dimension. */
const SIZE_CLASS: Record<ButtonSize, string> = {
    sm: "h-(--control-height-sm)",
    md: "h-(--control-height-md)",
    lg: "h-(--control-height-lg)",
};

/**
 * `neutral` and `danger` intentionally read differently from what a first
 * guess might reach for — see this batch's authoring report:
 * - `neutral` has no background of its own (matches the "Secondary action"
 *   swatch already proven in `frontend-web/app/storybook/page.tsx`), so it
 *   reads as a bordered button over whatever surface it sits on rather than
 *   a second, competing fill.
 * - `danger` is the one tone that owns a real background: `statusDanger` is
 *   documented in `themes/shared-roles.ts` as deliberately deep enough to
 *   carry `textOnSolid`, which is exactly the pairing used here so a
 *   destructive action reads as a real button, not a status badge. No
 *   hover/pressed shade exists for it in the token set, so state feedback
 *   comes from opacity rather than inventing a new colour role.
 */
const TONE_CLASS: Record<ButtonTone, string> = {
    primary: "bg-interactive-primary text-text-on-accent hover:bg-interactive-primary-hover active:bg-interactive-primary-pressed",
    neutral: "border border-border-default text-text-primary hover:bg-surface-row-hover active:bg-surface-selected",
    ghost: "text-text-primary hover:bg-surface-row-hover active:bg-surface-selected",
    danger: "bg-status-danger text-text-on-solid hover:opacity-90 active:opacity-80",
};

/**
 * The rotating ring is a `<style>` element with its own `@keyframes`, not
 * Tailwind's `animate-spin` utility: the adapter clears `--animate-*` to
 * `initial` (`theme/adapters/tailwind.css`) the same way it clears every
 * other namespace, so a theme-keyed `animate-spin` class would silently
 * resolve to nothing rather than fail loudly. A `<style>` tag with an
 * inline `animation` shorthand needs no theme key at all, and is excluded
 * from the accessible-name computation like any `<style>`/`<script>`, so it
 * cannot leak into the button's label.
 *
 * Placeholder per this component's `loading` contract — swap for the real
 * `Spinner` (COMPONENTS.md's "Status and feedback" row) once it exists, the
 * same "swap this in later" marker `Logo.tsx` already uses for its wordmark.
 */
function LoadingIndicator() {
    return (
        <>
            <style>{"@keyframes button-loading-spin { to { transform: rotate(360deg); } }"}</style>
            <span
                aria-hidden="true"
                className="inline-block size-inline shrink-0 rounded-pill border-2 border-current border-t-transparent"
                style={{ animation: "button-loading-spin 0.6s linear infinite" }}
            />
        </>
    );
}

/**
 * Tier 0 — behaviour is Base UI's real `Button` primitive (ADR-031):
 * keyboard activation (Enter/Space) and disabled semantics both work
 * correctly even when `render` swaps the element for a non-`<button>` (an
 * anchor via `nativeButton={false}`), which a bare `useRender` call would
 * not get for free. This component supplies only tokens and the smaller
 * public surface (`tone`, `size`, `loading`, icon slots) on top of it.
 */
export function Button({
    tone,
    size = "md",
    loading = false,
    leadingIcon,
    trailingIcon,
    children,
    className,
    disabled = false,
    ...rest
}: ButtonProps) {
    const isDisabled = disabled || loading;

    return (
        <BaseButton
            disabled={isDisabled}
            className={[BASE_CLASS, SIZE_CLASS[size], TONE_CLASS[tone], className].filter(Boolean).join(" ")}
            {...(loading ? { "aria-busy": true as const } : {})}
            {...rest}
        >
            {loading ? <LoadingIndicator /> : leadingIcon}
            {children}
            {loading ? null : trailingIcon}
        </BaseButton>
    );
}
