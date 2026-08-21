import type { CheckboxRootProps as BaseCheckboxRootProps } from "@base-ui/react/checkbox";
import { Checkbox as BaseCheckbox } from "@base-ui/react/checkbox";
import { Check, Minus } from "lucide-react";
import { CONTROL_ICON_CLASS, mergeStyle } from "../../lib";

export interface CheckboxOwnProps {
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * `checked`/`defaultChecked`/`onCheckedChange`, `indeterminate` and `value`
 * (for use inside `CheckboxGroup`) are all Base UI's own vocabulary,
 * verified against `@base-ui/react/checkbox`'s `CheckboxRoot.d.ts` — no
 * second state mechanism added here, same as `Toggle`.
 */
export type CheckboxProps = CheckboxOwnProps & Omit<BaseCheckboxRootProps, "className">;

const BOX_CLASS_NAME =
    "group inline-flex shrink-0 items-center justify-center rounded-chip border border-border-default bg-surface-inset text-text-on-accent transition-hover focus-visible:focus-ring data-[checked]:border-interactive-primary data-[checked]:bg-interactive-primary data-[indeterminate]:border-interactive-primary data-[indeterminate]:bg-interactive-primary aria-invalid:border-status-danger data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

/**
 * Tier 0 — thin styling wrapper over Base UI's `Checkbox` (ADR-031): checked/
 * indeterminate state, keyboard activation (Space; Enter is deliberately
 * left to submit an enclosing form, matching native `<input type="checkbox">`)
 * and disabled semantics all come from `@base-ui/react/checkbox`, which
 * emits `data-checked`/`data-indeterminate`/`data-disabled` rather than
 * classes — this component only maps those attributes onto tokens.
 *
 * Ships real, visible styling deliberately: a bare, unstyled Base UI
 * `Checkbox.Root` is a `<span>` with no native appearance at all (unlike a
 * plain `<input type="checkbox">`, which at least keeps the browser's own
 * box), so without an explicit border/background here it would be
 * invisible against the page — the same lesson `Input.tsx`'s own README
 * documents about Tailwind's preflight, more severe here since there is no
 * native rendering to fall back on. Verified by rendering unchecked,
 * checked and indeterminate side by side in `Checkbox.stories.tsx` and
 * confirming each has a distinct, visible treatment.
 *
 * The tick and dash glyphs both stay mounted whenever the indicator itself
 * is mounted, toggled between with `group-data-[indeterminate]:` rather
 * than a JS conditional on the incoming `indeterminate` prop — required,
 * not a style preference: a "select all" parent checkbox inside a
 * `CheckboxGroup` gets its indeterminate state computed internally by Base
 * UI from the group's own values, so the actual rendered state can diverge
 * from whatever `indeterminate` prop this component was called with. The
 * data attribute Base UI puts on `Checkbox.Indicator` is the one value
 * that is always correct.
 */
export function Checkbox({ className, style, ...props }: CheckboxProps) {
    return (
        <BaseCheckbox.Root
            className={ [BOX_CLASS_NAME, className].filter(Boolean).join(" ") }
            style={ mergeStyle(style, { width: "var(--control-box)", height: "var(--control-box)" }) }
            { ...props }
        >
            <BaseCheckbox.Indicator className="flex" keepMounted={ false }>
                <Check aria-hidden="true" className={`${CONTROL_ICON_CLASS} group-data-[indeterminate]:hidden`}/>
                <Minus aria-hidden="true" className={`${CONTROL_ICON_CLASS} hidden group-data-[indeterminate]:block`}/>
            </BaseCheckbox.Indicator>
        </BaseCheckbox.Root>
    );
}
