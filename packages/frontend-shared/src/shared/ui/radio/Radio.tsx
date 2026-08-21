import type { RadioRootProps as BaseRadioRootProps } from "@base-ui/react/radio";
import { Radio as BaseRadio } from "@base-ui/react/radio";
import { mergeStyle } from "../../lib";

export interface RadioOwnProps {
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * `value` (the unique id this radio answers to inside a `RadioGroup`) is
 * Base UI's own vocabulary, verified against `@base-ui/react/radio`'s
 * `RadioRoot.d.ts` — this component reads it straight through rather than
 * renaming it. `RadioRootProps` is itself generic over `Value`, so a
 * `RadioGroup<number>`/`Radio<number>` pairing (used by `RatingScale`,
 * built independently against the raw Base UI primitives rather than
 * through this file — see that component's README) works with no
 * string-conversion layer either place.
 */
export type RadioProps<Value = string> = RadioOwnProps & Omit<BaseRadioRootProps<Value>, "className">;

const CIRCLE_CLASS_NAME =
    "inline-flex shrink-0 items-center justify-center rounded-pill border border-border-default bg-surface-inset transition-hover focus-visible:focus-ring data-[checked]:border-interactive-primary aria-invalid:border-status-danger data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

/**
 * Tier 0 — thin styling wrapper over Base UI's `Radio` (ADR-031): checked
 * state, keyboard activation (Space; Enter is deliberately left inert — see
 * `RadioRoot.js`'s own "Radio only activates with Space" comment, verified
 * by reading the compiled source rather than assumed) and disabled
 * semantics all come from `@base-ui/react/radio`, which emits
 * `data-checked`/`data-unchecked`/`data-disabled` rather than classes —
 * this component only maps those attributes onto tokens.
 *
 * Ships real, visible styling for the same reason `Checkbox`'s own README
 * documents: Base UI's `Radio.Root` is a bare, unstyled `<span>` with no
 * native appearance to fall back on. The outer ring (`border`) and inner
 * dot (`Radio.Indicator`) are two separate elements, both required —
 * unlike `Checkbox`'s single tick glyph, a radio's selected state is
 * conventionally a smaller filled circle inside a larger ring, not a fill
 * of the whole shape (which would read as a solid button, not a radio).
 */
export function Radio<Value = string>({ className, style, ...props }: RadioProps<Value>) {
    return (
        <BaseRadio.Root
            className={ [CIRCLE_CLASS_NAME, className].filter(Boolean).join(" ") }
            style={ mergeStyle(style, { width: "var(--control-box)", height: "var(--control-box)" }) }
            { ...props }
        >
            <BaseRadio.Indicator
                className="rounded-pill bg-interactive-primary"
                style={ { width: "calc(var(--control-box) / 2)", height: "calc(var(--control-box) / 2)" } }
                keepMounted={ false }
            />
        </BaseRadio.Root>
    );
}
