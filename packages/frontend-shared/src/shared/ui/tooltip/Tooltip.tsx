"use client";

import { Tooltip as BaseTooltip } from "@base-ui/react/tooltip";

/**
 * `Root` and `Trigger` render no tokens of their own — see `Popover.tsx` for
 * the full reasoning behind re-exporting Base UI's parts directly rather
 * than adding a wrapper with nothing to add. `Provider` is included for the
 * same reason it exists upstream: wrap it once near a list of icon-only
 * controls (a table's action column, say) so every tooltip in that list
 * shares one open delay instead of each re-running its own.
 */
const TooltipRoot = BaseTooltip.Root;
const TooltipTrigger = BaseTooltip.Trigger;
const TooltipProvider = BaseTooltip.Provider;

export type TooltipRootProps<Payload = unknown> = BaseTooltip.Root.Props<Payload>;
export type TooltipTriggerProps<Payload = unknown> = BaseTooltip.Trigger.Props<Payload>;
export type TooltipProviderProps = BaseTooltip.Provider.Props;

export interface TooltipPopupOwnProps {
    readonly className?: string;
}

export type TooltipPopupProps = TooltipPopupOwnProps &
    Pick<BaseTooltip.Positioner.Props, "side" | "align" | "sideOffset" | "alignOffset" | "collisionPadding"> &
    Omit<BaseTooltip.Popup.Props, "className">;

/**
 * Lighter than `Popover`'s panel — `shadow-elevation1`, `text-caption` — a
 * hint is the least visually weighted overlay in the system, one rung below
 * an anchored panel that can hold real controls.
 */
/** No `max-w-*` cap — see `Popover.tsx`'s own `POPUP_CLASS` comment for why (`--container-*` is cleared and no token replaces it yet). */
const POPUP_CLASS =
    "w-max rounded-control border border-border-subtle bg-surface-elevated px-inline py-inline-tight text-caption text-text-secondary shadow-elevation1 outline-none transition-popover data-[starting-style]:opacity-0 data-[ending-style]:opacity-0";

export function TooltipPopup({ side, align, sideOffset = 6, alignOffset, collisionPadding, className, children, ...rest }: TooltipPopupProps) {
    return (
        <BaseTooltip.Portal>
            <BaseTooltip.Positioner
                side={side}
                align={align}
                sideOffset={sideOffset}
                {...(alignOffset === undefined ? {} : { alignOffset })}
                {...(collisionPadding === undefined ? {} : { collisionPadding })}
                className="z-tooltip outline-none"
            >
                <BaseTooltip.Popup className={[POPUP_CLASS, className].filter(Boolean).join(" ")} {...rest}>
                    {children}
                </BaseTooltip.Popup>
            </BaseTooltip.Positioner>
        </BaseTooltip.Portal>
    );
}

const ARROW_OFFSET_CLASS =
    "data-[side=top]:bottom-[-4px] data-[side=bottom]:top-[-4px] data-[side=left]:right-[-4px] data-[side=right]:left-[-4px]";

export type TooltipArrowProps = Omit<BaseTooltip.Arrow.Props, "className"> & { readonly className?: string };

/** See `Popover.tsx`'s own `ARROW_TIP_STYLE` for why this is an inline style rather than `size-1.5` — `var(--radius-chip)` is the role for that class's own un-clearable 0.375rem step. */
const ARROW_TIP_STYLE = { width: "var(--radius-chip)", height: "var(--radius-chip)" } as const;

/**
 * Same tip shape as `Popover.Arrow` — see it for the reasoning. `z-tooltip`
 * likewise moved to the Positioner in `TooltipPopup` above, not left on the
 * Popup class — see that function's own comment for why.
 */
export function TooltipArrow({ className, ...rest }: TooltipArrowProps) {
    return (
        <BaseTooltip.Arrow className={[ARROW_OFFSET_CLASS, className].filter(Boolean).join(" ")} {...rest}>
            <span style={ARROW_TIP_STYLE} className="block rotate-45 border border-border-subtle bg-surface-elevated" />
        </BaseTooltip.Arrow>
    );
}

/**
 * Tier 0 — a hint for sighted users, never the only carrier of information
 * (`COMPONENTS.md`): it has no `Close` part, because it holds no interactive
 * content of its own and is dismissed the same way it opened — hover-out,
 * blur, or Escape, all handled by `@base-ui/react/tooltip` (ADR-031).
 * Unlike `Popover.Trigger`, Base UI's own `Tooltip.Trigger` already opens on
 * hover and focus by default — no `openOnHover` flag to remember.
 */
export const Tooltip = {
    Root: TooltipRoot,
    Provider: TooltipProvider,
    Trigger: TooltipTrigger,
    Popup: TooltipPopup,
    Arrow: TooltipArrow,
};
