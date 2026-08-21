"use client";

import type { ReactNode } from "react";
import { Autocomplete as BaseAutocomplete } from "@base-ui/react/autocomplete";
import { ChevronDown, X } from "lucide-react";
import { IconButton } from "../icon-button";
import { Separator } from "../separator";
import { Text } from "../text";
import { CONTROL_ICON_CLASS } from "../../lib";

export type AutocompleteSize = "sm" | "md" | "lg";

/* ------------------------------------------------------------------------ */
/* Root                                                                      */
/* ------------------------------------------------------------------------ */

/**
 * A bare re-export, the same reasoning `Menu.Root`/`Select.Root` already
 * established for the identical shape: `Root` renders no DOM of its own,
 * and unlike `Combobox.Root` in this same batch, Base UI's own
 * `AutocompleteRoot` has no `multiple`/`Multiple` generic to narrow in the
 * first place — verified against its own `.d.ts` (two overloads, one for
 * grouped items, one flat, neither taking a selection-mode parameter),
 * since free-form text has no "several selected values" concept to guard
 * against the way `Combobox.tsx`'s own README documents for that component.
 */
const AutocompleteRoot = BaseAutocomplete.Root;
export type AutocompleteRootProps<ItemValue> = BaseAutocomplete.Root.Props<ItemValue>;

/* ------------------------------------------------------------------------ */
/* Label                                                                     */

/* ------------------------------------------------------------------------ */

/**
 * A native `<label htmlFor>`, not Base UI's own `Autocomplete.Label` — the
 * identical choice `Combobox.tsx`'s own `Label` makes, for the identical,
 * independently-re-verified reason: `Autocomplete.Label` shares `Combobox`'s
 * label-only-labels-the-trigger implementation upstream (`autocomplete/index.parts.d.ts`
 * aliases it straight to `ComboboxLabel`), so the same trade-off applies —
 * see `combobox/Combobox.tsx`'s own `Label` comment for the full reasoning
 * on why a native label's narrow, popup-open-only gap was chosen over
 * `Field.Label`'s permanent one.
 */
export interface AutocompleteLabelProps {
    readonly htmlFor: string;
    readonly children: ReactNode;
    readonly className?: string;
}

function Label({ htmlFor, children, className }: AutocompleteLabelProps) {
    return (
        <Text variant="small" color="primary" className={ className } render={ <label htmlFor={ htmlFor }/> }>
            { children }
        </Text>
    );
}

/* ------------------------------------------------------------------------ */
/* InputGroup + Input + Trigger + Clear                                     */
/* ------------------------------------------------------------------------ */

const HEIGHT_CLASS: Record<AutocompleteSize, string> = {
    sm: "h-(--control-height-sm)",
    md: "h-(--control-height-md)",
    lg: "h-(--control-height-lg)",
};

export interface AutocompleteInputGroupOwnProps {
    readonly children: ReactNode;
    /** @default "md" */
    readonly size?: AutocompleteSize;
    readonly className?: string;
}

export type AutocompleteInputGroupProps =
    AutocompleteInputGroupOwnProps
    & Omit<BaseAutocomplete.InputGroup.Props, "className" | "children">;

/**
 * The same bordered "box" `Combobox.tsx`'s own `InputGroup` ships, for the
 * same reason: a flex row (`Input` as `flex-1 min-w-0`, `Clear`/`Trigger` as
 * fixed-size flex siblings) reaches the identical flush-right icon layout
 * Base UI's own reference composition gets from hand-reserved absolute
 * positioning, with no padding-reservation arithmetic to keep in sync with
 * `size`. Duplicated rather than imported from `../combobox` — see this
 * component's own README for why duplication, not a shared module, is this
 * batch's consistent choice across all three list-of-values components.
 */
function InputGroup({ children, size = "md", className, ...rest }: AutocompleteInputGroupProps) {
    return (
        <BaseAutocomplete.InputGroup
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
        </BaseAutocomplete.InputGroup>
    );
}

export interface AutocompleteInputOwnProps {
    readonly className?: string;
}

export type AutocompleteInputProps = AutocompleteInputOwnProps & Omit<BaseAutocomplete.Input.Props, "className">;

function Input({ className, ...rest }: AutocompleteInputProps) {
    return (
        <BaseAutocomplete.Input
            className={ ["min-w-0 flex-1 bg-transparent text-body text-text-primary outline-none placeholder:text-text-muted", className]
                .filter(Boolean)
                .join(" ") }
            { ...rest }
        />
    );
}

export interface AutocompleteIconButtonOwnProps {
    /** The accessible name — an icon-only control with no name is not a valid button, same rule `IconButton`'s own `label` enforces. */
    readonly label: string;
    readonly children?: ReactNode;
    readonly className?: string;
}

function Trigger({ label, children, className }: AutocompleteIconButtonOwnProps) {
    return (
        <BaseAutocomplete.Trigger
            render={
                <IconButton label={ label } tone="ghost" size="sm" className={ className }>
                    { children ?? <ChevronDown className={CONTROL_ICON_CLASS}/> }
                </IconButton>
            }
        />
    );
}

function Clear({ label, children, className }: AutocompleteIconButtonOwnProps) {
    return (
        <BaseAutocomplete.Clear
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

export interface AutocompletePopupOwnProps {
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. Applied to the visible floating panel, not the invisible portal/positioner around it. */
    readonly className?: string;
}

export type AutocompletePopupProps = AutocompletePopupOwnProps &
    Pick<BaseAutocomplete.Positioner.Props, "side" | "align" | "sideOffset" | "alignOffset" | "collisionPadding">;

/**
 * Same positioning and token choices as `Combobox.tsx`'s own `Popup` — see
 * that component's README for the `z-popover`-on-the-Positioner finding
 * (re-verified, not re-derived, for this component) and the `align="start"`
 * reasoning.
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
               }: AutocompletePopupProps) {
    return (
        <BaseAutocomplete.Portal>
            <BaseAutocomplete.Positioner
                side={ side }
                align={ align }
                sideOffset={ sideOffset }
                alignOffset={ alignOffset }
                collisionPadding={ collisionPadding }
                className={ POSITIONER_CLASS }
            >
                <BaseAutocomplete.Popup
                    className={ [POPUP_CLASS, className].filter(Boolean).join(" ") }>{ children }</BaseAutocomplete.Popup>
            </BaseAutocomplete.Positioner>
        </BaseAutocomplete.Portal>
    );
}

/* ------------------------------------------------------------------------ */
/* List / Item / Group / GroupLabel / Separator / Empty                     */

/* ------------------------------------------------------------------------ */

export interface AutocompleteListOwnProps {
    readonly className?: string;
}

/** `children` is inherited from `BaseAutocomplete.List.Props` rather than redeclared here — see `Combobox.List`'s own identical reasoning (`../combobox/Combobox.tsx`) for why. */
export type AutocompleteListProps = AutocompleteListOwnProps & Omit<BaseAutocomplete.List.Props, "className">;

function List({ children, className }: AutocompleteListProps) {
    return (
        <BaseAutocomplete.List
            className={ ["max-h-(--available-height) overflow-y-auto", className].filter(Boolean).join(" ") }>
            { children }
        </BaseAutocomplete.List>
    );
}

export interface AutocompleteItemOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type AutocompleteItemProps =
    AutocompleteItemOwnProps
    & Omit<BaseAutocomplete.Item.Props, "children" | "className">;

/**
 * No reserved indicator column, unlike `Select.Item`/`Combobox.Item`: an
 * autocomplete suggestion only ever *fills in* the input text, it does not
 * persist as "the selected value" the way a `Select`/`Combobox` value does
 * (`AutocompleteItem.mjs` is, per Base UI's own source, literally
 * `ComboboxItem` reused — but this component chooses not to render its
 * `ItemIndicator`, since there is nothing to mark as selected once dismissed
 * from view). `cursor-default` and the highlighted/disabled classes still
 * match `Select.Item`/`Combobox.Item` exactly, since a suggestion is still
 * presented as a listbox option (`role="option"`, confirmed from the same
 * source), not a clickable action.
 */
const ITEM_CLASS =
    "flex w-full items-center px-stack py-inline text-body text-text-primary outline-none cursor-default select-none transition-hover data-[highlighted]:bg-surface-row-hover focus-visible:focus-ring data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

function Item({ children, className, ...rest }: AutocompleteItemProps) {
    return (
        <BaseAutocomplete.Item className={ [ITEM_CLASS, className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseAutocomplete.Item>
    );
}

export interface AutocompleteGroupProps {
    readonly children: ReactNode;
    readonly className?: string;
}

function Group({ children, className }: AutocompleteGroupProps) {
    return <BaseAutocomplete.Group className={ className }>{ children }</BaseAutocomplete.Group>;
}

export interface AutocompleteGroupLabelProps {
    readonly children: ReactNode;
    readonly className?: string;
}

function GroupLabel({ children, className }: AutocompleteGroupLabelProps) {
    return (
        <BaseAutocomplete.GroupLabel
            className={ ["px-stack py-inline-tight text-caption text-text-muted", className].filter(Boolean).join(" ") }>
            { children }
        </BaseAutocomplete.GroupLabel>
    );
}

export interface AutocompleteSeparatorProps {
    readonly className?: string;
}

/** This package's own `Separator` reused directly — see `Select.tsx`'s own README for why this, and not Base UI's own `Autocomplete.Separator`, is the consistent choice across this batch's list-of-values components. */
function ItemSeparator({ className }: AutocompleteSeparatorProps) {
    return <Separator className={ ["my-inline-tight", className].filter(Boolean).join(" ") }/>;
}

export interface AutocompleteEmptyProps {
    readonly children: ReactNode;
    readonly className?: string;
}

/** No default message — `COMPONENTS.md` §12: "copy arrives as props below Tier 3." */
function Empty({ children, className }: AutocompleteEmptyProps) {
    return (
        <BaseAutocomplete.Empty
            className={ ["px-stack py-inline text-body text-text-muted", className].filter(Boolean).join(" ") }>
            { children }
        </BaseAutocomplete.Empty>
    );
}

/* ------------------------------------------------------------------------ */

/**
 * Tier 0 — free text with suggestions, where the value need not be in the
 * list, per `COMPONENTS.md`. Behaviour (open/close, suggestion filtering,
 * optional inline autocompletion via `mode`, keyboard navigation, dismissal,
 * focus return) is entirely Base UI's own `@base-ui/react/autocomplete`
 * (ADR-031), verified against its own `.d.ts` and installed docs rather than
 * assumed — including its own explicit usage guidance ("use Combobox instead
 * if the selection should be remembered and the input value cannot be
 * custom... Autocomplete's input can contain free-form text, as its
 * suggestions only *optionally* autocomplete the text"), which is exactly
 * the line `COMPONENTS.md` draws between this component and `Combobox`.
 * Base UI's own installed package literally reuses several of `Combobox`'s
 * parts internally for this component (`Icon`, `Clear`, `List`, `Status`,
 * `Portal`, `Backdrop`, `Positioner`, `Popup`, `Arrow`, `Group`,
 * `GroupLabel`, `Row`, `Collection`, `Empty`, `Item` are, per
 * `autocomplete/index.parts.d.ts`, the identical `Combobox*` components
 * under an alias) — this module mirrors that closeness in its own visual
 * language while keeping its own implementation, matching token strings
 * rather than importing from `../combobox`, the consistent choice this
 * batch makes across all three list-of-values components (see this
 * component's own README).
 */
export const Autocomplete = {
    Root: AutocompleteRoot,
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
