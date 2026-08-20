"use client";

import { PreviewCard as BasePreviewCard } from "@base-ui/react/preview-card";

/** See `Popover.tsx` for why `Root` and `Trigger` are re-exported directly rather than wrapped with nothing to add. */
const PreviewCardRoot = BasePreviewCard.Root;
const PreviewCardTrigger = BasePreviewCard.Trigger;

export type PreviewCardRootProps<Payload = unknown> = BasePreviewCard.Root.Props<Payload>;

/**
 * Unlike `Popover.Trigger`/`Tooltip.Trigger`, Base UI's own `PreviewCard.Trigger`
 * always renders an `<a>` (verified against `PreviewCardTrigger.d.ts`: its
 * `BaseUIComponentProps<'a', ...>` base, no `render`-swap escape hatch
 * documented for a non-anchor case) — it exists specifically to preview
 * *linked* content, matching `COMPONENTS.md`'s own purpose for this
 * component: "hover preview for a linked job or contact." A caller always
 * supplies a real `href`.
 */
export type PreviewCardTriggerProps<Payload = unknown> = BasePreviewCard.Trigger.Props<Payload>;

export interface PreviewCardPopupOwnProps {
    readonly className?: string;
}

export type PreviewCardPopupProps = PreviewCardPopupOwnProps &
    Pick<BasePreviewCard.Positioner.Props, "side" | "align" | "sideOffset" | "alignOffset" | "collisionPadding"> &
    Omit<BasePreviewCard.Popup.Props, "className">;

/**
 * Same weight as `Popover`'s panel — `shadow-elevation2`, `rounded-card` —
 * rather than `Tooltip`'s lighter treatment: a preview card carries real
 * content (a job's title, company, status), not a one-line hint, so it
 * reads as a small `Surface`, the same call `Popover.Popup` already makes
 * for the same reason. `z-popover` lives on the Positioner, not the Popup —
 * see `Popover.tsx`'s own `PopoverPopup` comment for why.
 */
/** No `max-w-*` cap — see `Popover.tsx`'s own `POPUP_CLASS` comment for why (`--container-*` is cleared and no token replaces it yet). */
const POPUP_CLASS =
    "w-max rounded-card border border-border-subtle bg-surface-elevated p-stack text-body text-text-primary shadow-elevation2 outline-none transition-popover data-[starting-style]:opacity-0 data-[ending-style]:opacity-0";

export function PreviewCardPopup({ side, align, sideOffset = 8, alignOffset, collisionPadding, className, children, ...rest }: PreviewCardPopupProps) {
    return (
        <BasePreviewCard.Portal>
            <BasePreviewCard.Positioner
                side={side}
                align={align}
                sideOffset={sideOffset}
                {...(alignOffset === undefined ? {} : { alignOffset })}
                {...(collisionPadding === undefined ? {} : { collisionPadding })}
                className="z-popover outline-none"
            >
                <BasePreviewCard.Popup className={[POPUP_CLASS, className].filter(Boolean).join(" ")} {...rest}>
                    {children}
                </BasePreviewCard.Popup>
            </BasePreviewCard.Positioner>
        </BasePreviewCard.Portal>
    );
}

const ARROW_OFFSET_CLASS =
    "data-[side=top]:bottom-[-4px] data-[side=bottom]:top-[-4px] data-[side=left]:right-[-4px] data-[side=right]:left-[-4px]";

export type PreviewCardArrowProps = Omit<BasePreviewCard.Arrow.Props, "className"> & { readonly className?: string };

/** See `Popover.tsx`'s own `ARROW_TIP_STYLE` for why this is an inline style rather than `size-2`. */
const ARROW_TIP_STYLE = { width: "0.5rem", height: "0.5rem" } as const;

/** Same tip shape as `Popover.Arrow` — see it for the reasoning. */
export function PreviewCardArrow({ className, ...rest }: PreviewCardArrowProps) {
    return (
        <BasePreviewCard.Arrow className={[ARROW_OFFSET_CLASS, className].filter(Boolean).join(" ")} {...rest}>
            <span style={ARROW_TIP_STYLE} className="block rotate-45 border border-border-subtle bg-surface-elevated" />
        </BasePreviewCard.Arrow>
    );
}

/**
 * Tier 0 — hover preview for a linked job or contact. Behaviour (portal,
 * collision-flip positioning, hover-with-delay and keyboard-focus open,
 * hoverable-popup handoff via `safePolygon`, Escape/outside-dismiss, focus
 * return) is entirely Base UI's own (`@base-ui/react/preview-card`,
 * ADR-031); this module supplies only the tokens on top of it. No `Close`
 * part, for the same reason `Tooltip` has none — a preview is dismissed by
 * moving away, not by an explicit action inside it.
 */
export const PreviewCard = {
    Root: PreviewCardRoot,
    Trigger: PreviewCardTrigger,
    Popup: PreviewCardPopup,
    Arrow: PreviewCardArrow,
};
