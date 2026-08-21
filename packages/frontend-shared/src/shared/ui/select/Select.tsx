"use client";

import type { ReactNode } from "react";
import { Select as BaseSelect } from "@base-ui/react/select";
import { Check, ChevronsUpDown } from "lucide-react";
import { Separator } from "../separator";
import { Text } from "../text";
import { CONTROL_ICON_CLASS } from "../../lib";

export type SelectSize = "sm" | "md" | "lg";

/* ------------------------------------------------------------------------ */
/* Root                                                                      */
/* ------------------------------------------------------------------------ */

/**
 * A bare re-export — `Root` renders no DOM at all (`SelectRoot.d.ts`: "Doesn't
 * render its own HTML element"), the same reasoning `Menu.Root`/`Popover.Root`
 * already established for the identical shape: every visual decision lives on
 * `Trigger`/`Popup`/`Item` below, not here. `modal` is left at Base UI's own
 * `true` default — a short, known list being actively chosen from reads
 * correctly as a brief, page-scroll-locking interaction, the same intent
 * behind a native `<select>`'s own platform popup.
 */
const SelectRoot = BaseSelect.Root;
export type SelectRootProps<Value, Multiple extends boolean | undefined = false> = BaseSelect.Root.Props<Value, Multiple>;

/* ------------------------------------------------------------------------ */
/* Trigger + Value + Icon                                                    */
/* ------------------------------------------------------------------------ */

const HEIGHT_CLASS: Record<SelectSize, string> = {
    sm: "h-(--control-height-sm)",
    md: "h-(--control-height-md)",
    lg: "h-(--control-height-lg)",
};

export interface SelectTriggerOwnProps {
    readonly children: ReactNode;
    /** @default "md" */
    readonly size?: SelectSize;
    readonly className?: string;
}

export type SelectTriggerProps = SelectTriggerOwnProps & Omit<BaseSelect.Trigger.Props, "className" | "children">;

/**
 * Ships the same visible box `Input.tsx` ships, for the same reason: a
 * `Select` reads as a member of this system's field family (the row it sits
 * on in `COMPONENTS.md`'s own "Inputs" table), so its trigger gets the exact
 * border/background/radius treatment every other field control uses, not a
 * bespoke "button-that-looks-like-a-dropdown" style. `justify-between`
 * places `Select.Value` and `Select.Icon` at opposite ends, matching every
 * native and third-party select's own layout.
 */
function Trigger({ children, size = "md", className, ...rest }: SelectTriggerProps) {
    return (
        <BaseSelect.Trigger
            className={ [
                "inline-flex w-full items-center justify-between gap-inline-tight rounded-control border border-border-default bg-surface-inset px-inline text-body text-text-primary transition-hover focus-visible:focus-ring data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60",
                HEIGHT_CLASS[size],
                className,
            ]
                .filter(Boolean)
                .join(" ") }
            { ...rest }
        >
            { children }
        </BaseSelect.Trigger>
    );
}

export interface SelectValueOwnProps {
    readonly placeholder?: ReactNode;
    readonly className?: string;
}

export type SelectValueProps = SelectValueOwnProps & Omit<BaseSelect.Value.Props, "className" | "placeholder">;

/** `data-placeholder:text-text-muted` mirrors `Input.tsx`'s own placeholder treatment for the same visual role. */
function Value({ placeholder, className, ...rest }: SelectValueProps) {
    return (
        <BaseSelect.Value
            className={ ["truncate data-[placeholder]:text-text-muted", className].filter(Boolean).join(" ") }
            { ...(placeholder === undefined ? {} : { placeholder }) }
            { ...rest }
        />
    );
}

export interface SelectIconProps {
    readonly className?: string;
}

/**
 * Defaults to `lucide-react`'s `ChevronsUpDown` — the same reasoning
 * `PasswordField.tsx` already used to pick a glyph from this package's
 * already-installed icon dependency rather than waiting on `COMPONENTS.md`
 * §13's still-open `Icon` API: a bidirectional caret is the universal
 * "opens a choice list" affordance and is unlikely to be revisited once that
 * decision lands.
 */
function Icon({ className }: SelectIconProps) {
    return (
        <BaseSelect.Icon className={ ["shrink-0 text-text-muted", className].filter(Boolean).join(" ") }>
            <ChevronsUpDown className={CONTROL_ICON_CLASS}/>
        </BaseSelect.Icon>
    );
}

/* ------------------------------------------------------------------------ */
/* Popup (Portal + Positioner + Popup)                                      */

/* ------------------------------------------------------------------------ */

export interface SelectPopupOwnProps {
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. Applied to the visible floating panel, not the invisible portal/positioner around it. */
    readonly className?: string;
}

export type SelectPopupProps =
    SelectPopupOwnProps
    & Pick<BaseSelect.Positioner.Props, "align" | "alignOffset" | "collisionPadding">;

/**
 * `align`/`side`/`sideOffset` are left at Base UI's own defaults, unlike
 * `Menu.Popup`'s opinionated `align="start"` — Select's default positioning
 * deliberately overlaps the trigger so the selected item's text lines up
 * with the trigger's own value text (`alignItemWithTrigger`, true by
 * default; "Special positioning behavior" in Select's own usage guidelines),
 * which a differing default here would fight rather than complement.
 *
 * `min-w-(--anchor-width)` — Base UI's own CSS variable, exposed on the
 * `Positioner` and inherited by this descendant `Popup` — keeps the popup at
 * least as wide as the trigger, matching every native and third-party
 * select; no existing token names "at least as wide as my own anchor," so
 * this reads Base UI's variable directly, the same class of exception
 * `Grid.tsx`'s own `columns` prop already documents for values with no
 * meaningful token.
 *
 * `z-popover` on the Positioner, not the Popup — verified against
 * `Popover.tsx`'s and `Menu.tsx`'s own identical, independently-confirmed
 * finding: `position: fixed` (and therefore the only element a `z-index`
 * has any effect on) lives on the Positioner.
 */
const POSITIONER_CLASS = "z-popover";
const POPUP_CLASS =
    "min-w-(--anchor-width) max-h-(--available-height) overflow-y-auto rounded-card border border-border-subtle bg-surface-elevated p-inline-tight shadow-elevation2 outline-none transition-popover data-[starting-style]:opacity-0 data-[ending-style]:opacity-0";

function Popup({ children, className, align, alignOffset, collisionPadding }: SelectPopupProps) {
    return (
        <BaseSelect.Portal>
            <BaseSelect.Positioner align={ align } alignOffset={ alignOffset } collisionPadding={ collisionPadding }
                                   className={ POSITIONER_CLASS }>
                <BaseSelect.Popup
                    className={ [POPUP_CLASS, className].filter(Boolean).join(" ") }>{ children }</BaseSelect.Popup>
            </BaseSelect.Positioner>
        </BaseSelect.Portal>
    );
}

/* ------------------------------------------------------------------------ */
/* Item                                                                      */

/* ------------------------------------------------------------------------ */

export interface SelectItemOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type SelectItemProps = SelectItemOwnProps & Omit<BaseSelect.Item.Props, "children" | "className">;

/**
 * `cursor-default`, not `cursor-pointer` — a deliberate divergence from
 * `Menu.Item`'s own `cursor-pointer`, verified against Base UI's own
 * reference demo, which uses `cursor-default` for every `Select.Item`: a
 * menu item reads as a button-like action, while a select item is a row in
 * a list of values (a native `<select>`'s own `<option>` shows no pointer
 * cursor either), so the two intentionally look and feel different even
 * though both are floating, keyboard-navigable lists.
 *
 * The indicator sits in a fixed-width leading column that stays reserved
 * whether or not the current item is selected — `Select.ItemIndicator`
 * itself unmounts when unselected, and reserving its column regardless
 * avoids every other item's text shifting sideways as selection changes.
 */
const ITEM_CLASS =
    "flex w-full items-center gap-inline-tight rounded-control px-stack py-inline text-body text-text-primary outline-none cursor-default select-none transition-hover data-[highlighted]:bg-surface-row-hover focus-visible:focus-ring data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

function Item({ children, className, ...rest }: SelectItemProps) {
    return (
        <BaseSelect.Item className={ [ITEM_CLASS, className].filter(Boolean).join(" ") } { ...rest }>
            <span className="flex w-4 shrink-0 items-center justify-center">
                <BaseSelect.ItemIndicator>
                    <Check className={CONTROL_ICON_CLASS}/>
                </BaseSelect.ItemIndicator>
            </span>
            <BaseSelect.ItemText className="flex-1 text-left">{ children }</BaseSelect.ItemText>
        </BaseSelect.Item>
    );
}

/* ------------------------------------------------------------------------ */
/* Group / GroupLabel / Separator / Label                                   */

/* ------------------------------------------------------------------------ */

export interface SelectGroupProps {
    readonly children: ReactNode;
    readonly className?: string;
}

function Group({ children, className }: SelectGroupProps) {
    return <BaseSelect.Group className={ className }>{ children }</BaseSelect.Group>;
}

export interface SelectGroupLabelProps {
    readonly children: ReactNode;
    readonly className?: string;
}

function GroupLabel({ children, className }: SelectGroupLabelProps) {
    return (
        <BaseSelect.GroupLabel
            className={ ["px-stack py-inline-tight text-caption text-text-muted", className].filter(Boolean).join(" ") }>
            { children }
        </BaseSelect.GroupLabel>
    );
}

export interface SelectSeparatorProps {
    readonly className?: string;
}

/**
 * This package's own `Separator` reused directly, the same choice
 * `Menu.tsx`'s own `ItemSeparator` already made for the identical reason:
 * `Select.Separator`'s own documented surface (`orientation`/`className`/
 * `style`/`render`) is identical to the generic `@base-ui/react/separator`
 * this package's `Separator` already wraps, so there is no select-specific
 * behavior to preserve by rendering Base UI's own `Select.Separator`
 * instead.
 */
function ItemSeparator({ className }: SelectSeparatorProps) {
    return <Separator className={ ["my-inline-tight", className].filter(Boolean).join(" ") }/>;
}

export interface SelectLabelProps {
    readonly children: ReactNode;
    readonly className?: string;
}

/** Composes this package's own `Text` (Tier 0 composing Tier 0, `COMPONENTS.md` §2), the same choice `Field.tsx` already made for its own label. */
function Label({ children, className }: SelectLabelProps) {
    return (
        <Text variant="small" color="primary" className={ className } render={ <BaseSelect.Label/> }>
            { children }
        </Text>
    );
}

/* ------------------------------------------------------------------------ */

/**
 * Tier 0 — choice from a known short list, per `COMPONENTS.md`. Behaviour
 * (open/close, roving highlight, keyboard typeahead, edge-collision
 * positioning that by default overlaps the trigger to align the selected
 * item, dismissal, focus return) is entirely Base UI's own `@base-ui/react/select`
 * (ADR-031) — verified against its own `.d.ts` and installed docs, not
 * assumed, including the explicit "prefer `Combobox` once a list needs
 * filtering" guidance in Base UI's own usage notes, which is exactly the
 * line `COMPONENTS.md` draws between this component and `Combobox`. This
 * module supplies only tokens and a smaller compound surface —
 * `Root`/`Trigger`/`Value`/`Icon`/`Popup`/`Item`/`Group`/`GroupLabel`/
 * `Separator`/`Label` — sharing `Menu.tsx`'s own visual language for a
 * floating list (`z-popover` placement, `rounded-card`/`shadow-elevation2`
 * popup, `data-[highlighted]:bg-surface-row-hover` items) without literally
 * importing from it: `Menu`'s own parts are action-oriented (`leadingIcon`,
 * `shortcut`) and do not carry `Select`'s value-selection semantics
 * (`selected`, `ItemIndicator`, `ItemText`), so each gets its own
 * implementation, matching identical token strings the same way
 * `Menu.tsx`'s and `Popover.tsx`'s own popup styling already duplicate
 * rather than share a module.
 */
export const Select = {
    Root: SelectRoot,
    Trigger,
    Value,
    Icon,
    Popup,
    Item,
    Group,
    GroupLabel,
    Separator: ItemSeparator,
    Label,
};
