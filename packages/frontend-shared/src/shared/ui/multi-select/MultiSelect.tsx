"use client";

import type { ReactNode } from "react";
import { Combobox as BaseCombobox } from "@base-ui/react/combobox";
import { X } from "lucide-react";
import { Combobox } from "../combobox";
import type { TagTone } from "../tag";

export type MultiSelectSize = "sm" | "md" | "lg";

/* ------------------------------------------------------------------------ */
/* Root                                                                      */
/* ------------------------------------------------------------------------ */

/**
 * The mirror image of `Combobox.tsx`'s own `Root`, which drops `multiple` (and its type
 * parameter) from the public surface because that module never renders chips. This module is
 * the dedicated multi-value entry point `combobox/README.md` and `combobox/Combobox.tsx`'s own
 * comments name explicitly — so `multiple` is fixed to `true` instead, again removed from the
 * public surface (`SKILL.md` §3.4's "make the invalid combination a type error" applied to a
 * whole feature, the same choice `Combobox.Root` already made in the opposite direction).
 */
export type MultiSelectRootProps<Value> = Omit<BaseCombobox.Root.Props<Value, true>, "multiple">;

function Root<Value>(props: MultiSelectRootProps<Value>) {
    return <BaseCombobox.Root multiple { ...props } />;
}

/* ------------------------------------------------------------------------ */
/* InputGroup — a real divergence from Combobox's own, not a copy            */
/* ------------------------------------------------------------------------ */

const MIN_HEIGHT_CLASS: Record<MultiSelectSize, string> = {
    sm: "min-h-(--control-height-sm)",
    md: "min-h-(--control-height-md)",
    lg: "min-h-(--control-height-lg)",
};

export interface MultiSelectInputGroupOwnProps {
    readonly children: ReactNode;
    /** @default "md" */
    readonly size?: MultiSelectSize;
    readonly className?: string;
}

export type MultiSelectInputGroupProps =
    MultiSelectInputGroupOwnProps
    & Omit<BaseCombobox.InputGroup.Props, "className" | "children">;

/**
 * `Combobox.tsx`'s own `InputGroup` is reused directly for every part below where the
 * underlying Base UI primitive is genuinely identical (see this component's own README for
 * why) — but not here. A single-line text field's box has a *fixed* height
 * (`Combobox.InputGroup`'s own `HEIGHT_CLASS`); a chips field's box has to grow as chips wrap
 * onto a second or third row, which a fixed height cannot do. `min-h-*` plus real vertical
 * padding (`px-inline py-inline-tight`, not `Combobox.InputGroup`'s `pl-inline` alone, which
 * relies on a fixed single-line height to center content vertically) replaces the fixed height
 * — verified as a real, load-bearing difference by reading `ComboboxInput.js` directly: typing
 * with no chips present renders one short row, and adding chips visibly grows the container in
 * `MultiSelect.stories.tsx`'s own demo.
 */
function InputGroup({ children, size = "md", className, ...rest }: MultiSelectInputGroupProps) {
    return (
        <BaseCombobox.InputGroup
            className={ [
                "flex w-full flex-wrap items-center gap-inline-tight rounded-control border border-border-default bg-surface-inset px-inline py-inline-tight transition-hover focus-within:focus-ring data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60",
                MIN_HEIGHT_CLASS[size],
                className,
            ]
                .filter(Boolean)
                .join(" ") }
            { ...rest }
        >
            { children }
        </BaseCombobox.InputGroup>
    );
}

/* ------------------------------------------------------------------------ */
/* Chips container + Value (the render-prop that reads the selected array)  */

/* ------------------------------------------------------------------------ */

export interface MultiSelectChipsProps {
    readonly children: ReactNode;
    readonly className?: string;
}

/** Layout only — `flex-wrap`, so chips wrap onto additional rows rather than overflowing. `role="toolbar"` (needed so NVDA stays in focus mode while arrow-navigating chips) is automatic, applied by Base UI itself only once a chip actually exists (`hasSelectionChips`, read directly from `ComboboxChips.js`) — nothing to add here. */
function Chips({ children, className }: MultiSelectChipsProps) {
    return <BaseCombobox.Chips
        className={ ["flex flex-wrap items-center gap-inline-tight", className].filter(Boolean).join(" ") }>{ children }</BaseCombobox.Chips>;
}

/**
 * A bare re-export, the same reasoning `NumberField.tsx`'s own `Root` gives for the identical
 * choice: `Combobox.Value` renders no HTML element of its own at all ("Doesn't render its own
 * HTML element," per its own `.d.ts`) — it is purely a render-prop handing the caller the
 * current selected-value array, with zero visual decision to own. The official composition
 * (base-ui.com's own multi-select demo, read directly rather than guessed) nests it inside
 * `Chips`, mapping over the array to render `Chip`/`ChipRemove` per selected value.
 */
const Value = BaseCombobox.Value;

/* ------------------------------------------------------------------------ */
/* Chip / ChipRemove — Tag's exact visual language, on Base UI's real parts */
/* ------------------------------------------------------------------------ */

export type MultiSelectChipTone = TagTone;

/**
 * The exact same strings `tag/Tag.tsx` uses for its own `TONE_CLASS` — duplicated, not shared
 * via a module import of `Tag` itself, for a reason verified from source rather than assumed:
 * see this component's own README for why `Tag` (a fixed-props function component with no
 * `render` support, no forwarded ref, and no spread of extra DOM attributes onto its root
 * element) genuinely cannot receive the `tabIndex`/`onKeyDown`/ref plumbing `BaseCombobox.Chip`
 * needs to inject for its own real keyboard machinery. Reusing the *type* (`TagTone`) keeps the
 * tone vocabulary itself DRY even though the rendering can't be.
 */
const CHIP_TONE_CLASS: Record<MultiSelectChipTone, string> = {
    neutral: "border-border-default bg-surface-inset text-text-secondary",
    info: "border-status-info bg-status-info-subtle text-status-info-text",
    attention: "border-status-attention bg-status-attention-subtle text-status-attention-text",
    success: "border-status-success bg-status-success-subtle text-status-success-text",
    danger: "border-status-danger bg-status-danger-subtle text-status-danger-text",
};

/**
 * `rounded-chip`/`px-inline`/`py-inline-tight`/`text-small`, matching `Tag.tsx`'s own
 * `BASE_CLASS` exactly. `focus-visible:focus-ring` is a genuine, deliberate addition `Tag`'s
 * own outer `<span>` does not carry: `Tag` is never itself focusable (only its inner button
 * is), while `BaseCombobox.Chip` renders a real, focusable element — read directly from
 * `ComboboxChip.js`, it sets `tabIndex: -1` (never in normal Tab order, matching the standard
 * ARIA combobox-with-chips pattern: chips are reached via ArrowLeft from the input or via
 * Backspace, never Tab) but still receives real, programmatic DOM focus during arrow-key
 * roving navigation between chips, which `:focus-visible` correctly matches for a
 * keyboard-originated focus call.
 */
const CHIP_BASE_CLASS = "inline-flex items-center gap-inline-tight whitespace-nowrap rounded-chip border px-inline py-inline-tight text-small outline-none focus-visible:focus-ring";

export interface MultiSelectChipOwnProps {
    /** @default "neutral" */
    readonly tone?: MultiSelectChipTone;
    readonly children: ReactNode;
    readonly className?: string;
}

export type MultiSelectChipProps = MultiSelectChipOwnProps & Omit<BaseCombobox.Chip.Props, "className" | "children">;

/**
 * Composes the real `BaseCombobox.Chip` — not `Tag` — so the roving arrow-key navigation
 * between chips, Backspace/Delete-to-remove-while-focused, and ArrowUp/Down-to-reopen-the-list
 * keyboard machinery `ComboboxChip.js` implements (read directly, not assumed) survives intact.
 * See this component's own README for the full reasoning on why `Tag` itself was ruled out
 * rather than blindly composed.
 */
function Chip({ tone = "neutral", children, className, ...rest }: MultiSelectChipProps) {
    return (
        <BaseCombobox.Chip
            className={ [CHIP_BASE_CLASS, CHIP_TONE_CLASS[tone], className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseCombobox.Chip>
    );
}

/** The exact same `REMOVE_BUTTON_CLASS` string `Tag.tsx` uses for its own dismiss button — same sizing rationale (WCAG 2.2's 24×24 CSS px target on a ~24px-tall chip, too small for `IconButton`'s own 32px-minimum square). */
const REMOVE_BUTTON_CLASS = "rounded-control p-inline-tight text-current opacity-70 transition-hover hover:opacity-100 focus-visible:focus-ring";
const ICON_SIZE = 16;

export interface MultiSelectChipRemoveProps {
    /** The accessible name — an icon-only control with no name is not a valid button, same rule `Tag`'s own `removeLabel` and every other icon-only control in this package enforce. Named `label`, not `removeLabel`, to match this package's other icon-only-button convention (`NumberField.Increment`/`Decrement`, `Combobox.Trigger`/`Clear`) — `Chip`/`ChipRemove` are two separate composable parts, architecturally closer to those than to `Tag`'s own single fused component. */
    readonly label: string;
    /** @default a lucide X glyph, matching `Tag`'s own default */
    readonly children?: ReactNode;
    readonly className?: string;
}

/**
 * `BaseCombobox.ChipRemove` sets no internal default `aria-label` of its own — verified by
 * reading `ComboboxChipRemove.js` directly — unlike `NumberField.Increment`/`Decrement`'s real
 * internal default, so there is no `lessons-learned.mdc`-documented merge-order race to guard
 * against here: a plain `aria-label={label}` prop is sufficient.
 */
function ChipRemove({ label, children, className }: MultiSelectChipRemoveProps) {
    return (
        <BaseCombobox.ChipRemove aria-label={ label }
                                 className={ [REMOVE_BUTTON_CLASS, className].filter(Boolean).join(" ") }>
            { children ?? <X size={ ICON_SIZE } aria-hidden="true"/> }
        </BaseCombobox.ChipRemove>
    );
}

/* ------------------------------------------------------------------------ */

/**
 * Tier 0 — several values as removable tags, per `COMPONENTS.md`. `Root`/`InputGroup`/`Chips`/
 * `Value`/`Chip`/`ChipRemove` are this module's own — the parts that genuinely differ once
 * `multiple: true` is real. Every other part (`Input`/`Trigger`/`Clear`/`Popup`/`List`/`Item`/
 * `Group`/`GroupLabel`/`Separator`/`Empty`) is `Combobox`'s own, reused directly rather than
 * duplicated — see this component's own README for why that is a deliberate departure from the
 * Select/Combobox/Menu precedent of duplicating matching strings, not an oversight.
 */
export const MultiSelect = {
    Root,
    Label: Combobox.Label,
    InputGroup,
    Chips,
    Value,
    Chip,
    ChipRemove,
    Input: Combobox.Input,
    Trigger: Combobox.Trigger,
    Clear: Combobox.Clear,
    Popup: Combobox.Popup,
    List: Combobox.List,
    Item: Combobox.Item,
    Group: Combobox.Group,
    GroupLabel: Combobox.GroupLabel,
    Separator: Combobox.Separator,
    Empty: Combobox.Empty,
};
