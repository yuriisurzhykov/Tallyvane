"use client";

import { Popover as BasePopover } from "@base-ui/react/popover";
import { IconButton } from "../icon-button";

/**
 * `Root` and `Trigger` render no tokens of their own — `Root` renders no DOM
 * at all, and `Trigger` is a bare interactive element whose actual look
 * always comes from whatever the caller composes into it (`render={<Button
 * .../>}` or `<IconButton .../>`, per `COMPONENTS.md` §2's "Tier 0 composing
 * Tier 0"). Re-exporting Base UI's own parts directly — rather than writing
 * a wrapper that adds nothing — is the same reasoning `Button.tsx`'s own
 * `AsAnchor` composition already relies on, applied one layer up: a wrapper
 * that only renames a prop doesn't earn its place (`SKILL.md` §7).
 */
const PopoverRoot = BasePopover.Root;
const PopoverTrigger = BasePopover.Trigger;

export type PopoverRootProps<Payload = unknown> = BasePopover.Root.Props<Payload>;
export type PopoverTriggerProps<Payload = unknown> = BasePopover.Trigger.Props<Payload>;

export interface PopoverPopupOwnProps {
    /** Renders `Popover.Arrow` pointing back at the trigger. @default false */
    readonly arrow?: boolean;
    readonly className?: string;
}

/**
 * Positioning props a caller might reasonably override, picked off
 * `Positioner.Props` (Base UI flattens `UseAnchorPositioningSharedParameters`
 * onto it, rather than exporting that type on its own). The rest — `anchor`,
 * `positionMethod`, `collisionBoundary`, `sticky`, `arrowPadding`,
 * `disableAnchorTracking`, `collisionAvoidance` — stays at Base UI's own
 * defaults until a real call site needs to reach past them (YAGNI).
 */
export type PopoverPopupProps = PopoverPopupOwnProps &
    Pick<BasePopover.Positioner.Props, "side" | "align" | "sideOffset" | "alignOffset" | "collisionPadding"> &
    Omit<BasePopover.Popup.Props, "className">;

/**
 * `w-max`, no `max-w-*` cap: Tailwind's `max-w-*` scale reads from
 * `--container-*`, which the adapter clears deliberately (`tailwind.css`:
 * "so `max-w-md` and friends cannot offer a second, foreign set of widths
 * alongside these") — a class from it would compile to nothing, not to a
 * narrower guess. No token names a floating panel's maximum width today
 * (`Menu.tsx`'s own `Popup` makes the same choice, for the same reason), so
 * this panel grows to fit its content unconstrained until one exists.
 */
const POPUP_CLASS =
    "w-max rounded-card border border-border-subtle bg-surface-elevated p-stack text-body text-text-primary shadow-elevation2 outline-none transition-popover data-[starting-style]:opacity-0 data-[ending-style]:opacity-0";

/**
 * Bundles Base UI's `Portal` > `Positioner` > `Popup` nesting behind one
 * part: a caller reaching for an anchored panel should not have to
 * rediscover that exact three-level structure every time, and the "portal
 * to `document.body`" rule (`COMPONENTS.md`'s Overlays section) is easiest
 * to keep true when there is exactly one place that renders `Portal`.
 *
 * `z-popover` lives on the Positioner, not the Popup: verified by rendering
 * that Base UI's `Positioner` is the element actually carrying
 * `position: fixed`, while `Popup` itself has no `position` of its own — a
 * `z-index` has no effect on a statically positioned element per the CSS
 * spec (the same finding `Menu.tsx`'s own `Popup` documents).
 */
export function PopoverPopup({
    side,
    align,
    sideOffset = 8,
    alignOffset,
    collisionPadding,
    arrow = false,
    className,
    children,
    ...rest
}: PopoverPopupProps) {
    return (
        <BasePopover.Portal>
            <BasePopover.Positioner
                side={side}
                align={align}
                sideOffset={sideOffset}
                {...(alignOffset === undefined ? {} : { alignOffset })}
                {...(collisionPadding === undefined ? {} : { collisionPadding })}
                className="z-popover outline-none"
            >
                <BasePopover.Popup className={[POPUP_CLASS, className].filter(Boolean).join(" ")} {...rest}>
                    {children}
                </BasePopover.Popup>
                {arrow ? <PopoverArrow /> : null}
            </BasePopover.Positioner>
        </BasePopover.Portal>
    );
}

const ARROW_OFFSET_CLASS =
    "data-[side=top]:bottom-[-4px] data-[side=bottom]:top-[-4px] data-[side=left]:right-[-4px] data-[side=right]:left-[-4px]";

export type PopoverArrowProps = Omit<BasePopover.Arrow.Props, "className"> & { readonly className?: string };

/**
 * The tip's own `width`/`height` are an inline style, not `size-2`: that
 * class reads from the same cleared `--spacing` multiplier `Drawer.tsx`'s
 * own `FULL_VIEWPORT_STYLE` documents, and compiles to nothing under it —
 * confirmed live by rendering, not re-verified independently here. `0.5rem`
 * matches Tailwind's own un-clearable default step for `2` exactly, so this
 * is not a new value, only the same one written where it still resolves.
 */
const ARROW_TIP_STYLE = { width: "0.5rem", height: "0.5rem" } as const;

/** A small rotated square, borderless on the two edges that meet the panel — the standard "speech bubble" tip. */
export function PopoverArrow({ className, ...rest }: PopoverArrowProps) {
    return (
        <BasePopover.Arrow className={[ARROW_OFFSET_CLASS, className].filter(Boolean).join(" ")} {...rest}>
            <span style={ARROW_TIP_STYLE} className="block rotate-45 border border-border-subtle bg-surface-elevated" />
        </BasePopover.Arrow>
    );
}

/**
 * A placeholder glyph — real icons arrive once `Icon`'s own API is decided
 * (`COMPONENTS.md` §13). Kept private to this file rather than shared,
 * since minting a shared close-icon component would itself be a quiet
 * answer to that still-open decision.
 */
function DismissGlyph() {
    return (
        <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
            <path d="M2 2l8 8M10 2l-8 8" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round" />
        </svg>
    );
}

export interface PopoverCloseProps {
    /** The accessible name for the close button — required, per `IconButton`'s own contract. */
    readonly label: string;
    readonly className?: string;
}

/** Composes `IconButton` for the dismiss affordance, per this batch's convention for every overlay's "x" (see `IconButton.tsx`). */
export function PopoverClose({ label, className }: PopoverCloseProps) {
    return (
        <BasePopover.Close
            render={
                <IconButton label={label} tone="ghost" size="sm" {...(className ? { className } : {})}>
                    <DismissGlyph />
                </IconButton>
            }
        />
    );
}

/**
 * Tier 0 — anchored panel with collision handling. Behaviour (portal,
 * Floating UI positioning with edge-collision flip, dismiss-on-outside-click,
 * Escape-to-close, focus return to the trigger) is entirely Base UI's own
 * (`@base-ui/react/popover`, ADR-031); this module supplies only the tokens
 * on top of it. Non-modal by default (Base UI's own default), matching the
 * `z-popover` role's stated intent — "anchored to a trigger, dismissed by
 * clicking away" — rather than the `scrim`/`modal` pair `Drawer` uses.
 */
export const Popover = {
    Root: PopoverRoot,
    Trigger: PopoverTrigger,
    Popup: PopoverPopup,
    Arrow: PopoverArrow,
    Close: PopoverClose,
};
