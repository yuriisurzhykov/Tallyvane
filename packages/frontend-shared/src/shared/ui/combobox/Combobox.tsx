"use client";

import type { ReactNode } from "react";
import { Combobox as BaseCombobox } from "@base-ui/react/combobox";
import { Check, ChevronDown, X } from "lucide-react";
import { IconButton } from "../icon-button";
import { Separator } from "../separator";
import { Text } from "../text";
import { CONTROL_ICON_CLASS } from "../../lib";

export type ComboboxSize = "sm" | "md" | "lg";

/* ------------------------------------------------------------------------ */
/* Root                                                                      */
/* ------------------------------------------------------------------------ */

/**
 * A real wrapper function, not a bare re-export like `Menu.Root`/`Select.Root`
 * — deliberately, and only, to drop the `multiple` prop and its `Multiple`
 * type parameter from the public surface. `MultiSelect` (`COMPONENTS.md`'s
 * own row: "several values as removable tags," composing `Tag` and this
 * component) is the dedicated multi-value entry point, a later, separate
 * batch — enabling Base UI's own `multiple` here today would silently turn
 * on chip-rendering machinery (`Combobox.Chips`/`Chip`/`ChipRemove`) this
 * module never renders, producing a value that changes with no visible chips
 * to show it. Omitting the prop at the type level (`SKILL.md` §3.4's "make
 * the invalid combination a type error, not a runtime footgun," applied to a
 * whole feature rather than one prop pairing) is safer than documenting "do
 * not pass `multiple`" and hoping.
 */
export type ComboboxRootProps<Value> = Omit<BaseCombobox.Root.Props<Value>, "multiple">;

function Root<Value>(props: ComboboxRootProps<Value>) {
    return <BaseCombobox.Root { ...props } />;
}

/* ------------------------------------------------------------------------ */
/* Labeling — deliberately no `Combobox.Label` part                         */

/* ------------------------------------------------------------------------ */

/**
 * A real bug, caught by Base UI's own runtime warning rather than assumed
 * away: an earlier draft of this component exposed a `Label` part composing
 * `BaseCombobox.Label`, mirroring `Select.tsx`'s own `Label`. Rendering it
 * logged, verbatim: "`<Combobox.Label>` labels `<Combobox.Trigger>` only.
 * When `<Combobox.Input>` is the form control, use a native `<label>` or
 * `<Field.Label>` instead." — and the rendered DOM confirmed it: the
 * accessible name landed on the (`tabIndex={-1}`, purely decorative) trigger
 * button, not on the actual `<input>` a screen reader user types into.
 * Reading `ComboboxLabel.mjs` directly confirmed this is not configurable:
 * it hardcodes the trigger (or the root, if the input renders inside the
 * popup) as the labelled element and explicitly deletes any `id` override
 * a caller passes ("ignore runtime id overrides from untyped consumers").
 *
 * The two remaining options both have a real, measured cost, weighed rather
 * than guessed: a plain `<label htmlFor>` sits outside the anchor/floating
 * pair Floating UI's `markOthers` exempts from `aria-hidden` while the popup
 * is open, so the field's accessible name is unavailable for that narrow
 * window — narrow because a screen reader user actively navigating the open
 * listbox is hearing option text, not the field's own name, during exactly
 * that window. `Field.Label`'s `aria-labelledby` broadcasts to *every*
 * labelable control sharing its `Field.Root`, permanently overwriting the
 * `Trigger`/`Clear` buttons' own distinct `aria-label`s the whole time the
 * field exists, not just while a popup happens to be open. A native
 * `<label htmlFor>` was chosen as the smaller, temporally-bounded cost.
 *
 * Composes this package's own `Text`, matching `Select.Label`'s and
 * `Field.tsx`'s own choice (Tier 0 composing Tier 0, `COMPONENTS.md` §2).
 * `htmlFor` is a required prop, not internally generated: `BaseCombobox.Input`
 * accepts a plain passthrough `id`, and coordinating the two through a
 * shared `useId()` at the call site is the ordinary React pattern for this,
 * not something a Tier 0 primitive should reach into Base UI's own
 * undocumented internal store to automate.
 */
export interface ComboboxLabelProps {
    readonly htmlFor: string;
    readonly children: ReactNode;
    readonly className?: string;
}

function Label({ htmlFor, children, className }: ComboboxLabelProps) {
    return (
        <Text variant="small" color="primary" className={ className } render={ <label htmlFor={ htmlFor }/> }>
            { children }
        </Text>
    );
}

/* ------------------------------------------------------------------------ */
/* InputGroup + Input + Trigger + Clear                                     */
/* ------------------------------------------------------------------------ */

const HEIGHT_CLASS: Record<ComboboxSize, string> = {
    sm: "h-(--control-height-sm)",
    md: "h-(--control-height-md)",
    lg: "h-(--control-height-lg)",
};

export interface ComboboxInputGroupOwnProps {
    readonly children: ReactNode;
    /** @default "md" */
    readonly size?: ComboboxSize;
    readonly className?: string;
}

export type ComboboxInputGroupProps =
    ComboboxInputGroupOwnProps
    & Omit<BaseCombobox.InputGroup.Props, "className" | "children">;

/**
 * The bordered "box" — the same visible-styling job `Input.tsx` does for a
 * plain text field, given to this compound field's own container instead of
 * to `Input` alone, so `Input`/`Trigger`/`Clear` can sit inside it as a
 * plain flex row with no border of their own. A flex row was chosen over
 * Base UI's own reference composition (which absolutely positions
 * `Clear`/`Trigger` over the input, reserving trailing padding by hand) —
 * that shape exists upstream because their input sits inside a taller,
 * label-including relative container; a bordered flex row achieves the
 * identical flush-right icon layout with no padding-reservation arithmetic
 * to keep in sync with `size`, which `PasswordField.tsx`'s own
 * `toggleInsetFor` helper needs precisely because it cannot restructure
 * `Input`'s existing box the way this component can restructure its own.
 */
function InputGroup({ children, size = "md", className, ...rest }: ComboboxInputGroupProps) {
    return (
        <BaseCombobox.InputGroup
            className={ [
                "flex w-full items-center gap-inline-tight rounded-control border border-border-default bg-surface-inset pl-inline transition-hover focus-within:focus-ring data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60",
                HEIGHT_CLASS[size],
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

export interface ComboboxInputOwnProps {
    readonly className?: string;
}

export type ComboboxInputProps = ComboboxInputOwnProps & Omit<BaseCombobox.Input.Props, "className">;

function Input({ className, ...rest }: ComboboxInputProps) {
    return (
        <BaseCombobox.Input
            className={ ["min-w-0 flex-1 bg-transparent text-body text-text-primary outline-none placeholder:text-text-muted", className]
                .filter(Boolean)
                .join(" ") }
            { ...rest }
        />
    );
}

export interface ComboboxIconButtonOwnProps {
    /** The accessible name — an icon-only control with no name is not a valid button, same rule `IconButton`'s own `label` enforces. */
    readonly label: string;
    readonly children?: ReactNode;
    readonly className?: string;
}

function Trigger({ label, children, className }: ComboboxIconButtonOwnProps) {
    return (
        <BaseCombobox.Trigger
            render={
                <IconButton label={ label } tone="ghost" size="sm" className={ className }>
                    { children ?? <ChevronDown className={CONTROL_ICON_CLASS}/> }
                </IconButton>
            }
        />
    );
}

function Clear({ label, children, className }: ComboboxIconButtonOwnProps) {
    return (
        <BaseCombobox.Clear
            render={
                <IconButton label={ label } tone="ghost" size="sm" className={ className }>
                    { children ?? <X className={CONTROL_ICON_CLASS}/> }
                </IconButton>
            }
        />
    );
}

/* ------------------------------------------------------------------------ */
/* Popup (Portal + Positioner + Popup)                                      */

/* ------------------------------------------------------------------------ */

export interface ComboboxPopupOwnProps {
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. Applied to the visible floating panel, not the invisible portal/positioner around it. */
    readonly className?: string;
}

export type ComboboxPopupProps = ComboboxPopupOwnProps &
    Pick<BaseCombobox.Positioner.Props, "side" | "align" | "sideOffset" | "alignOffset" | "collisionPadding">;

/**
 * `z-popover` on the Positioner, not the Popup — the same, independently
 * re-verified finding `Menu.tsx`/`Popover.tsx`/`Select.tsx` each already
 * document for their own floating panel. `align="start"` matches
 * `Menu.Popup`'s own opinionated default over Base UI's own `"center"`: a
 * combobox list hangs from its input's leading edge, the same reasoning
 * `Menu.tsx` gives for a dropdown menu, and unlike `Select.tsx`, which
 * deliberately leaves Base UI's default alone because Select has its own
 * special item-aligned overlap behavior this component does not share.
 * `min-w-(--anchor-width)`, matching `Select.tsx`'s own choice, so a long or
 * remote list's popup is never narrower than the field selecting into it.
 */
const POSITIONER_CLASS = "z-popover";
const POPUP_CLASS =
    "min-w-(--anchor-width) rounded-card border border-border-subtle bg-surface-elevated p-inline-tight shadow-elevation2 outline-none transition-popover data-[starting-style]:opacity-0 data-[ending-style]:opacity-0";

function Popup({
                   children,
                   className,
                   side,
                   align = "start",
                   sideOffset = 8,
                   alignOffset,
                   collisionPadding
               }: ComboboxPopupProps) {
    return (
        <BaseCombobox.Portal>
            <BaseCombobox.Positioner
                side={ side }
                align={ align }
                sideOffset={ sideOffset }
                alignOffset={ alignOffset }
                collisionPadding={ collisionPadding }
                className={ POSITIONER_CLASS }
            >
                <BaseCombobox.Popup
                    className={ [POPUP_CLASS, className].filter(Boolean).join(" ") }>{ children }</BaseCombobox.Popup>
            </BaseCombobox.Positioner>
        </BaseCombobox.Portal>
    );
}

/* ------------------------------------------------------------------------ */
/* List / Item / Group / GroupLabel / Separator / Empty                     */

/* ------------------------------------------------------------------------ */

export interface ComboboxListOwnProps {
    readonly className?: string;
}

/**
 * `children` is inherited from `BaseCombobox.List.Props` rather than
 * redeclared here: Base UI's own type is `ReactNode | ((item: any, index)
 * => ReactNode)`, since the real item type varies with `Combobox.Root`'s
 * own `Value` generic, which this part has no independent way to know.
 * Redeclaring it with `unknown` in place of `any` would reject every
 * real-world typed callback a caller would actually write (function
 * parameters are contravariant); redeclaring it with `any` directly would
 * trip this repo's own `no-explicit-any` lint rule. Inheriting the type
 * keeps the same real-world usability Base UI's own docs demonstrate
 * without this file itself writing `any`.
 */
export type ComboboxListProps = ComboboxListOwnProps & Omit<BaseCombobox.List.Props, "className">;

/** `max-h-(--available-height)` caps a genuinely long or remote list's popup, unlike `Select`'s own list, which has no equivalent cap since it exists for known-short lists only. */
function List({ children, className }: ComboboxListProps) {
    return (
        <BaseCombobox.List
            className={ ["max-h-(--available-height) overflow-y-auto", className].filter(Boolean).join(" ") }>
            { children }
        </BaseCombobox.List>
    );
}

export interface ComboboxItemOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type ComboboxItemProps = ComboboxItemOwnProps & Omit<BaseCombobox.Item.Props, "children" | "className">;

/**
 * Shares `Select.Item`'s exact visual language (fixed-width reserved
 * indicator column, `cursor-default`, the same highlighted/disabled classes)
 * for the same reason `Select.tsx`'s own README gives for sharing `Menu`'s:
 * both are "choose one value from a floating list" rows, and a product
 * where they looked different for no reason would read as inconsistent.
 * Duplicated rather than imported from `../select`, the same choice
 * `Menu.tsx`/`Popover.tsx`/`Select.tsx` already make independently for
 * their own popups — see this component's own README for the fuller
 * reasoning on why duplication, not a shared module, is this batch's
 * consistent choice across all three list-of-values components.
 */
const ITEM_CLASS =
    "flex w-full items-center gap-inline-tight rounded-control px-stack py-inline text-body text-text-primary outline-none cursor-default select-none transition-hover data-[highlighted]:bg-surface-row-hover focus-visible:focus-ring data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

function Item({ children, className, ...rest }: ComboboxItemProps) {
    return (
        <BaseCombobox.Item className={ [ITEM_CLASS, className].filter(Boolean).join(" ") } { ...rest }>
            <span className="flex w-4 shrink-0 items-center justify-center">
                <BaseCombobox.ItemIndicator>
                    <Check className={CONTROL_ICON_CLASS}/>
                </BaseCombobox.ItemIndicator>
            </span>
            <span className="flex-1 text-left">{ children }</span>
        </BaseCombobox.Item>
    );
}

export interface ComboboxGroupProps {
    readonly children: ReactNode;
    readonly className?: string;
}

function Group({ children, className }: ComboboxGroupProps) {
    return <BaseCombobox.Group className={ className }>{ children }</BaseCombobox.Group>;
}

export interface ComboboxGroupLabelProps {
    readonly children: ReactNode;
    readonly className?: string;
}

function GroupLabel({ children, className }: ComboboxGroupLabelProps) {
    return (
        <BaseCombobox.GroupLabel
            className={ ["px-stack py-inline-tight text-caption text-text-muted", className].filter(Boolean).join(" ") }>
            { children }
        </BaseCombobox.GroupLabel>
    );
}

export interface ComboboxSeparatorProps {
    readonly className?: string;
}

/** This package's own `Separator` reused directly — see `Select.tsx`'s own README for why this, and not Base UI's own `Combobox.Separator`, is the consistent choice across this batch's list-of-values components. */
function ItemSeparator({ className }: ComboboxSeparatorProps) {
    return <Separator className={ ["my-inline-tight", className].filter(Boolean).join(" ") }/>;
}

export interface ComboboxEmptyProps {
    readonly children: ReactNode;
    readonly className?: string;
}

/** No default message — `COMPONENTS.md` §12: "copy arrives as props below Tier 3," and "no results for company names" reads differently than "no results for tags." */
function Empty({ children, className }: ComboboxEmptyProps) {
    return (
        <BaseCombobox.Empty
            className={ ["px-stack py-inline text-body text-text-muted", className].filter(Boolean).join(" ") }>
            { children }
        </BaseCombobox.Empty>
    );
}

/* ------------------------------------------------------------------------ */

/**
 * Tier 0 — choice from a long or remote list: company, contact, tag, per
 * `COMPONENTS.md`. Behaviour (open/close, filtering — either Base UI's own
 * default `contains` match against `items`, or fully external via
 * `filteredItems`/`useFilter` for a remote, server-searched list — roving
 * highlight, keyboard navigation, dismissal, focus return) is entirely Base
 * UI's own `@base-ui/react/combobox` (ADR-031), verified against its own
 * `.d.ts` and installed docs rather than assumed — including its own
 * explicit usage guidance ("a filterable Select... prefer Combobox when the
 * number of items is sufficiently large to warrant filtering," "does not
 * allow free-form text input, for that use Autocomplete instead"), which is
 * exactly the line `COMPONENTS.md` draws between `Select`, `Combobox` and
 * `Autocomplete`. This module supplies only tokens, the `multiple`-prop
 * restriction above, and a smaller compound surface —
 * `Root`/`Label`/`InputGroup`/`Input`/`Trigger`/`Clear`/`Popup`/`List`/
 * `Item`/`Group`/`GroupLabel`/`Separator`/`Empty` — composing this package's
 * own `IconButton` and `Text` (Tier 0 composing Tier 0, `COMPONENTS.md` §2)
 * for the two icon-only buttons and the label respectively.
 */
export const Combobox = {
    Root,
    Label,
    InputGroup,
    Input,
    Trigger,
    Clear,
    Popup,
    List,
    Item,
    Group,
    GroupLabel,
    Separator: ItemSeparator,
    Empty,
};
