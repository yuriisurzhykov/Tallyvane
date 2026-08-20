import { Input as BaseInput, type InputProps as BaseInputProps } from "@base-ui/react/input";

export type InputSize = "sm" | "md" | "lg";

export interface InputOwnProps {
    /** @default "md" */
    readonly size?: InputSize;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * The native `size` attribute (a character-width count) has no place in this
 * design system's variant naming — `size` here means the `control` height
 * step, matching `Button`'s and `IconButton`'s own vocabulary for the same
 * word. Omitting it is required, not stylistic: without it, the inherited
 * native `size?: number` and this component's own `size?: InputSize` would
 * be two incompatible types for the same key.
 */
export type InputProps = InputOwnProps & Omit<BaseInputProps, "className" | "size">;

const SIZE_CLASS: Record<InputSize, string> = {
    sm: "h-(--control-height-sm)",
    md: "h-(--control-height-md)",
    lg: "h-(--control-height-lg)",
};

const BASE_CLASS_NAME =
    "w-full rounded-control border border-border-default bg-surface-inset px-inline text-body text-text-primary placeholder:text-text-muted transition-hover focus-visible:focus-ring aria-invalid:border-status-danger data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

/**
 * Tier 0 — single-line text, per `COMPONENTS.md`. A thin styling wrapper
 * over Base UI's `Input` (ADR-031): `Input` itself renders `Field.Control`
 * internally, so it already "just works" as `Field`'s rendered child, or
 * dropped directly into a bare `Field.Root`/`Form` without this package's
 * `Field` wrapper at all.
 *
 * Ships real, visible styling — background, border, padding, radius, focus
 * ring — deliberately: a plain unstyled `<input>` inherits `background:
 * transparent; border-width: 0; padding: 0` from this project's Tailwind
 * preflight, rendering functionally invisible against the page. This is the
 * component that fixes that for every text field in the product, which is
 * also why `Field.stories.tsx` now demos this instead of a bare `<input>`.
 *
 * The disabled treatment matches `Button`'s and `Toggle`'s own
 * `data-[disabled]:opacity-60 data-[disabled]:cursor-not-allowed` pair, since
 * `Input` sets the same `data-disabled` attribute Base UI emits everywhere
 * else. The invalid treatment keys off `aria-invalid` rather than
 * `data-invalid`, though: `Field.Control` guarantees the former reaches the
 * rendered element even when a caller sets it directly (this component's
 * own tests do exactly that, without a `Field.Root` ancestor at all) —
 * `data-invalid` is only ever computed from a real field's validity state.
 */
export function Input({ size = "md", className, ...props }: InputProps) {
    return <BaseInput className={[BASE_CLASS_NAME, SIZE_CLASS[size], className].filter(Boolean).join(" ")} {...props} />;
}
