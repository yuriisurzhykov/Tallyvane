"use client";

import type { ReactNode } from "react";
import { ContextMenu as BaseContextMenu } from "@base-ui/react/context-menu";
import { Menu as BaseMenuParts } from "@base-ui/react/menu";
import { KeyboardKey } from "../keyboard-key";
import { Separator } from "../separator";

/**
 * `menu/Menu.tsx` already existed by the time this component was built —
 * its item/separator/positioner conventions are reused directly below
 * rather than invented a second time, per this batch's brief. The one
 * genuine difference: `@base-ui/react/context-menu` ships only its own
 * `Root`/`Trigger` (verified against `context-menu/index.d.ts`) and
 * re-exports every other part — `Portal`, `Positioner`, `Popup`, `Arrow`,
 * `Group`, `GroupLabel`, `Item`, `Separator`, ... — straight from
 * `@base-ui/react/menu`, so this file imports those shared parts from the
 * `menu` package directly instead of a `context-menu`-namespaced copy that
 * does not exist.
 */
const ContextMenuRoot = BaseContextMenu.Root;
const ContextMenuTrigger = BaseContextMenu.Trigger;

export type ContextMenuRootProps = BaseContextMenu.Root.Props;
export type ContextMenuTriggerProps = BaseContextMenu.Trigger.Props;

export interface ContextMenuPopupOwnProps {
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. Applied to the visible floating panel. */
    readonly className?: string;
}

export type ContextMenuPopupProps = ContextMenuPopupOwnProps;

/**
 * `z-popover` lives on the Positioner, not the Popup — the same finding
 * `Menu.tsx`'s own `Popup` documents (verified there by rendering; not
 * re-verified independently here since the underlying primitive is the
 * same `@base-ui/react/menu` positioner either way).
 *
 * A right-click menu has no anchor `side`/`align` to expose: it opens at
 * the pointer's position, not against a trigger edge, so — unlike
 * `Popover`/`Menu`'s own dropdown positioning props — this component takes
 * none. `sideOffset={2}` (tighter than `Menu.tsx`'s `8`) keeps the panel
 * from the cursor position it opened at rather than a trigger edge, so it
 * needs less clearance to read as "at the pointer," not "away from it."
 */
/** `w-max` alone, matching `Menu.tsx`'s own `Popup` — no token names a minimum menu width, and `--spacing-*`'s bare multiplier (which `min-w-40` would have read) is deliberately cleared. */
const POPUP_CLASS =
    "w-max rounded-card border border-border-subtle bg-surface-elevated p-inline-tight shadow-elevation2 outline-none transition-popover data-[starting-style]:opacity-0 data-[ending-style]:opacity-0";

function ContextMenuPopup({ children, className }: ContextMenuPopupProps) {
    return (
        <BaseContextMenu.Portal>
            <BaseContextMenu.Positioner sideOffset={2} className="z-popover outline-none">
                <BaseContextMenu.Popup className={[POPUP_CLASS, className].filter(Boolean).join(" ")}>{children}</BaseContextMenu.Popup>
            </BaseContextMenu.Positioner>
        </BaseContextMenu.Portal>
    );
}

export interface ContextMenuItemOwnProps {
    readonly leadingIcon?: ReactNode;
    /** See `Menu.tsx`'s own `shortcut` prop for the full reasoning — reused verbatim here for the same reason its item styling is. */
    readonly shortcut?: ReactNode;
    readonly children: ReactNode;
    readonly className?: string;
}

export type ContextMenuItemProps = ContextMenuItemOwnProps & Omit<BaseMenuParts.Item.Props, "children" | "className">;

/** Identical token classes to `Menu.tsx`'s own `Item` — see it for the full reasoning; both are `@base-ui/react/menu`'s `MenuItem` underneath, so one visual language, not two. */
const ITEM_CLASS =
    "flex w-full items-center gap-inline-tight rounded-control px-stack py-inline text-body text-text-primary outline-none cursor-pointer select-none transition-hover data-[highlighted]:bg-surface-row-hover focus-visible:focus-ring data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

function ContextMenuItem({ leadingIcon, shortcut, children, className, ...rest }: ContextMenuItemProps) {
    return (
        <BaseMenuParts.Item className={[ITEM_CLASS, className].filter(Boolean).join(" ")} {...rest}>
            {leadingIcon}
            <span className="flex-1 text-left">{children}</span>
            {shortcut !== undefined ? <KeyboardKey>{shortcut}</KeyboardKey> : null}
        </BaseMenuParts.Item>
    );
}

export interface ContextMenuSeparatorProps {
    readonly className?: string;
}

/** This package's own `Separator`, reused directly — see `Menu.tsx`'s own `ItemSeparator` for the full reasoning. */
function ContextMenuSeparator({ className }: ContextMenuSeparatorProps) {
    return <Separator className={["my-inline-tight", className].filter(Boolean).join(" ")} />;
}

/**
 * Tier 0 — right-click actions on table rows. Behaviour (right-click and
 * long-press to open, positioning at the pointer, roving focus, typeahead,
 * outside-press/Escape dismissal, focus return) is Base UI's own
 * (`@base-ui/react/context-menu` composing `@base-ui/react/menu`, ADR-031).
 * Item and separator styling is intentionally identical to `Menu.tsx`'s,
 * since both wrap the exact same underlying `MenuItem`/`Separator` — a
 * right-click menu and a dropdown menu are the same kind of list to a
 * reader, just opened differently.
 */
export const ContextMenu = {
    Root: ContextMenuRoot,
    Trigger: ContextMenuTrigger,
    Popup: ContextMenuPopup,
    Item: ContextMenuItem,
    Separator: ContextMenuSeparator,
};
