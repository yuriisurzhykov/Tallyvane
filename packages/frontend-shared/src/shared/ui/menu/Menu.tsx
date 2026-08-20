"use client";

import type { ReactNode } from "react";
import { Menu as BaseMenu } from "@base-ui/react/menu";
import { KeyboardKey } from "../keyboard-key";
import { Separator } from "../separator";

/* ------------------------------------------------------------------------ */
/* Root + Trigger                                                            */
/* ------------------------------------------------------------------------ */

/**
 * `Root` and `Trigger` render no tokens of their own — `Root` renders no DOM
 * at all (confirmed against `MenuRoot.d.ts`'s own doc comment), and `Trigger`
 * is a bare interactive element whose actual look always comes from
 * whatever the caller composes into it via `render` (e.g.
 * `<Menu.Trigger render={<Button tone="neutral">Actions</Button>} />`),
 * mirroring `Popover.tsx`'s own established reasoning for this exact pair —
 * "Trigger is a bare interactive element whose actual look always comes
 * from whatever the caller composes into it" — applied one layer up, the
 * same way `Button.tsx`'s own `AsAnchor` composition already does. A
 * wrapper that only renames a prop doesn't earn its place (`SKILL.md` §7),
 * so these are re-exported directly rather than wrapped.
 */
const MenuRoot = BaseMenu.Root;
const MenuTrigger = BaseMenu.Trigger;

export type MenuRootProps<Payload = unknown> = BaseMenu.Root.Props<Payload>;
export type MenuTriggerProps<Payload = unknown> = BaseMenu.Trigger.Props<Payload>;

/* ------------------------------------------------------------------------ */
/* Popup (Portal + Positioner + Popup)                                      */
/* ------------------------------------------------------------------------ */

export interface MenuPopupOwnProps {
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. Applied to the visible floating panel, not the invisible portal/positioner around it. */
    readonly className?: string;
}

/**
 * Positioning props a caller might reasonably override, picked off
 * `Positioner.Props` — the same narrow surface `Popover.tsx`'s own
 * `PopoverPopupProps` already picks for the identical reason: the rest
 * (`anchor`, `positionMethod`, `collisionBoundary`, `sticky`,
 * `arrowPadding`, `disableAnchorTracking`, `collisionAvoidance`) stays at
 * Base UI's own defaults until a real call site needs to reach past them
 * (YAGNI).
 */
export type MenuPopupProps = MenuPopupOwnProps &
    Pick<BaseMenu.Positioner.Props, "side" | "align" | "sideOffset" | "alignOffset" | "collisionPadding">;

/**
 * `z-popover` — "menus, dropdowns... anything anchored to a trigger and
 * dismissed by clicking away" (`semantic/z-index.ts`) — sits on the
 * positioner, not the popup: verified by rendering this component and
 * inspecting the actual DOM (not assumed) that Base UI's own `MenuPositioner`
 * is the element carrying `position: fixed`, while `MenuPopup` itself gets
 * no `position` of its own — a `z-index` has no effect on a statically
 * positioned element per the CSS spec, so it has to live on the element
 * that is actually positioned for cross-overlay stacking (Toast above an
 * open Menu, say) to work once more than one portalled overlay exists at
 * once. `Popover.tsx`'s own `PopoverPopup` puts `z-popover` on the *popup*
 * class instead — see this batch's authoring report for why this component
 * does not copy that placement.
 *
 * `bg-surface-elevated` and `shadow-elevation2` mirror `Popover.tsx`'s own
 * choices for the same kind of floating panel; `rounded-card` (not
 * `rounded-control`) matches `Popover.tsx` too, since this is a
 * `Surface`-shaped floating container, not a control. `transition-popover`
 * plus the `data-[starting-style]`/`data-[ending-style]` opacity hooks are
 * the exact enter/exit treatment `Popover.tsx` already established for
 * Base UI's shared open/close transition-state attributes.
 */
const POSITIONER_CLASS = "z-popover";
const POPUP_CLASS =
    "w-max rounded-card border border-border-subtle bg-surface-elevated p-inline-tight shadow-elevation2 outline-none transition-popover data-[starting-style]:opacity-0 data-[ending-style]:opacity-0";

/**
 * Base UI's own `Portal` → `Positioner` → `Popup` nesting is fixed and never
 * reordered by a caller, so collapsing all three into one exported part
 * removes ceremony without losing anything a caller would want to vary —
 * the same reasoning, and the same three-level bundling, `Popover.tsx`'s
 * own `PopoverPopup` already uses. `align="start"` is this component's own
 * opinionated default (Base UI's own default is `"center"`, verified
 * against `useAnchorPositioning.d.ts`) — a menu conventionally hangs from
 * its trigger's leading edge rather than centering under it, unlike a
 * general-purpose `Popover`, which is why this default differs from
 * `PopoverPopup`'s (which leaves `align` at Base UI's own default).
 * `sideOffset={8}` matches `PopoverPopup`'s own default exactly.
 */
function Popup({ children, className, side, align = "start", sideOffset = 8, alignOffset, collisionPadding }: MenuPopupProps) {
    return (
        <BaseMenu.Portal>
            <BaseMenu.Positioner
                side={side}
                align={align}
                sideOffset={sideOffset}
                alignOffset={alignOffset}
                collisionPadding={collisionPadding}
                className={POSITIONER_CLASS}
            >
                <BaseMenu.Popup className={[POPUP_CLASS, className].filter(Boolean).join(" ")}>{children}</BaseMenu.Popup>
            </BaseMenu.Positioner>
        </BaseMenu.Portal>
    );
}

/* ------------------------------------------------------------------------ */
/* Item                                                                      */
/* ------------------------------------------------------------------------ */

export interface MenuItemOwnProps {
    readonly leadingIcon?: ReactNode;
    /**
     * Rendered inside this package's own `KeyboardKey`, so a caller passes
     * the label content only ("⌘K") rather than remembering to wrap it
     * itself — the one thing genuinely specific to a menu's shortcut
     * column, per `COMPONENTS.md`'s Tier 0 row for `Menu`. Deliberately one
     * slot, not a `keys` array: no known call site needs a multi-key combo
     * rendered as visually separate key caps yet (YAGNI), and `KeyboardKey`
     * itself already documents composing several instances with a
     * separator for that case if one arrives.
     */
    readonly shortcut?: ReactNode;
    readonly children: ReactNode;
    readonly className?: string;
}

export type MenuItemProps = MenuItemOwnProps & Omit<BaseMenu.Item.Props, "children" | "className">;

/**
 * `data-highlighted`/`data-disabled` are Base UI's own item state attributes
 * (`MenuItemDataAttributes`, verified against `MenuItemDataAttributes.d.ts`)
 * — real roving focus moves a real `tabIndex` onto the highlighted item
 * (`useMenuItemCommonProps.mjs`: `tabIndex: open && highlighted ? 0 : -1`),
 * so `focus-visible:focus-ring` genuinely applies per item rather than
 * needing a hand-rolled focus indicator. A disabled item stays reachable by
 * arrow-key navigation (Base UI's own `MenuRoot` passes `disabledIndices:
 * []` to Floating UI's list navigation — verified by reading `MenuRoot.mjs`
 * directly, not assumed) and is only blocked from activating, matching the
 * ARIA Authoring Practices menu pattern's own guidance that disabled items
 * may stay perceivable and navigable while not being actionable.
 */
const ITEM_CLASS =
    "flex w-full items-center gap-inline-tight rounded-control px-stack py-inline text-body text-text-primary outline-none cursor-pointer select-none transition-hover data-[highlighted]:bg-surface-row-hover focus-visible:focus-ring data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

function Item({ leadingIcon, shortcut, children, className, ...rest }: MenuItemProps) {
    return (
        <BaseMenu.Item className={[ITEM_CLASS, className].filter(Boolean).join(" ")} {...rest}>
            {leadingIcon}
            <span className="flex-1 text-left">{children}</span>
            {shortcut !== undefined ? <KeyboardKey>{shortcut}</KeyboardKey> : null}
        </BaseMenu.Item>
    );
}

/* ------------------------------------------------------------------------ */
/* Separator                                                                 */
/* ------------------------------------------------------------------------ */

export interface MenuSeparatorProps {
    readonly className?: string;
}

/**
 * This package's own `Separator` reused directly — Tier 0 composing Tier 0,
 * `COMPONENTS.md` §2 — rather than the raw, unstyled `@base-ui/react/separator`
 * that Base UI's own `Menu.Separator` re-exports upstream (confirmed against
 * `menu/index.parts.d.ts`: `export { Separator } from "../separator/Separator.js"`).
 * The only thing genuinely specific to a menu is the vertical rhythm against
 * its neighbouring items, which this wrapper adds via `my-inline-tight`; the
 * hairline colour and `role="separator"` semantics stay exactly what
 * `Separator` already provides.
 */
function ItemSeparator({ className }: MenuSeparatorProps) {
    return <Separator className={["my-inline-tight", className].filter(Boolean).join(" ")} />;
}

/* ------------------------------------------------------------------------ */

/**
 * Tier 0 — a dropdown menu with roving focus, typeahead, and a shortcut
 * column. Behaviour (open/close, roving `tabIndex`, arrow-key and typeahead
 * navigation, outside-press/Escape dismissal, focus return to the trigger)
 * is Base UI's `@base-ui/react/menu` (ADR-031); this component supplies
 * only tokens and a smaller compound surface — `Root`/`Trigger`/`Popup`/
 * `Item`/`Separator` — composing `KeyboardKey` for each item's shortcut
 * (Tier 0 with no domain knowledge of its own, per `COMPONENTS.md` §2's
 * composition rule) and, at the call site, this package's own `Button` for
 * the trigger's visual layer.
 */
export const Menu = {
    Root: MenuRoot,
    Trigger: MenuTrigger,
    Popup,
    Item,
    Separator: ItemSeparator,
};
