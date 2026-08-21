import type { CheckboxRootProps as BaseCheckboxRootProps } from "@base-ui/react/checkbox";
import { Checkbox as BaseCheckbox } from "@base-ui/react/checkbox";
import { Check, Minus } from "lucide-react";

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

/**
 * No spacing role names "how big a checkbox is" any more than one names
 * "how thick a scrollbar is" (`ScrollArea.tsx`'s own `SCROLLBAR_THICKNESS`) —
 * a checkbox box is geometry, not a spacing decision. `1.25rem` reads
 * clearly next to `text-body`'s 1.125rem/`text-small`'s 1.0625rem label
 * text without dwarfing it, and the same constant is reused (each file
 * owning its own copy, per that precedent) for `Radio`'s box and
 * `Slider`'s thumb so this batch's small controls share one implied scale.
 */
const BOX_SIZE = "1.25rem";

const ICON_SIZE = 14;

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
            style={ { ...style, width: BOX_SIZE, height: BOX_SIZE } }
            { ...props }
        >
            <BaseCheckbox.Indicator className="flex" keepMounted={ false }>
                <Check size={ ICON_SIZE } aria-hidden="true" className="group-data-[indeterminate]:hidden"/>
                <Minus size={ ICON_SIZE } aria-hidden="true" className="hidden group-data-[indeterminate]:block"/>
            </BaseCheckbox.Indicator>
        </BaseCheckbox.Root>
    );
}
