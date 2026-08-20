"use client";

import type { ReactNode } from "react";
import { Drawer as BaseDrawer } from "@base-ui/react/drawer";
import { IconButton } from "../icon-button";
import { Text } from "../text";

/** See `Popover.tsx` for why `Trigger` is re-exported directly rather than wrapped with nothing to add. */
const DrawerTrigger = BaseDrawer.Trigger;

export type DrawerTriggerProps<Payload = unknown> = BaseDrawer.Trigger.Props<Payload>;

export type DrawerRootProps<Payload = unknown> = BaseDrawer.Root.Props<Payload>;

/**
 * The one place this module overrides a Base UI default: `swipeDirection`
 * defaults to `'down'` (a bottom sheet) upstream, but every drawer in this
 * product is the side creation panel (`COMPONENTS.md`: "with modals banned,
 * everything that would have been 'new item' dialog is a drawer"), so
 * `'right'` is this component's own default — still Base UI's real prop,
 * still overridable, just not left at an upstream default that would slide
 * the wrong way for every call site here.
 */
function DrawerRoot<Payload = unknown>({ swipeDirection = "right", ...props }: DrawerRootProps<Payload>) {
    return <BaseDrawer.Root swipeDirection={swipeDirection} {...props} />;
}

/**
 * A genuinely tokenless numeric — no semantic spacing role names "how wide a
 * drawer is" any more than one names "how thick a scrollbar is"
 * (`ScrollArea.tsx`'s own `SCROLLBAR_THICKNESS`), and this batch's brief
 * forbids adding one. One visual treatment, no size variant, until a real
 * call site needs a second width.
 */
const DRAWER_WIDTH = "28rem";

/**
 * An inline style, not a `fixed inset-0` class pair: `inset-0` reads from
 * Tailwind's bare `--spacing` multiplier, which the adapter clears on
 * purpose (`tailwind.css`: "so a system with sm/md/lg spacing keys... Without
 * it, only the roles below produce a utility and **anything else produces
 * nothing**") — confirmed live, not assumed, by rendering this component in
 * a real browser and finding the Viewport's computed `top`/`right`/`bottom`/
 * `left` all still `auto`, `inset-0` having compiled to nothing at all. `0`
 * is not a semantic spacing decision to name a token for; it is geometry
 * ("cover the exact viewport"), the same class of exception `Grid.tsx`'s own
 * `columns` inline style already documents.
 */
const FULL_VIEWPORT_STYLE = { position: "fixed", inset: 0 } as const;

export interface DrawerPopupOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type DrawerPopupProps = DrawerPopupOwnProps & Omit<BaseDrawer.Popup.Props, "className" | "children">;

/**
 * Bundles `Portal` > `Backdrop` + `Viewport` > `Popup`: the same "reduce
 * boilerplate, keep behaviour real" reasoning as `Popover.Popup`. `modal`
 * defaults to `true` upstream (verified against `DrawerRoot.d.ts`), which is
 * exactly what makes this the load-bearing one — focus trap and page-scroll
 * lock both come from that default, not from anything written here.
 */
function DrawerPopup({ className, children, ...rest }: DrawerPopupProps) {
    return (
        <BaseDrawer.Portal>
            <BaseDrawer.Backdrop
                style={FULL_VIEWPORT_STYLE}
                className="z-scrim bg-surface-overlay transition-popover data-[starting-style]:opacity-0 data-[ending-style]:opacity-0"
            />
            <BaseDrawer.Viewport style={FULL_VIEWPORT_STYLE} className="z-modal flex items-stretch justify-end">
                <BaseDrawer.Popup
                    className={[
                        "flex h-full flex-col gap-stack overflow-y-auto border-l border-border-subtle bg-surface-elevated p-stack text-body text-text-primary shadow-elevation3 outline-none transition-drawer data-[starting-style]:translate-x-full data-[ending-style]:translate-x-full",
                        className,
                    ]
                        .filter(Boolean)
                        .join(" ")}
                    style={{ width: DRAWER_WIDTH }}
                    {...rest}
                >
                    {children}
                </BaseDrawer.Popup>
            </BaseDrawer.Viewport>
        </BaseDrawer.Portal>
    );
}

export interface DrawerTitleProps {
    readonly children: ReactNode;
    readonly className?: string;
}

/** Composes `Text` for the drawer's heading — see `Field.tsx` for the same `render={<Base... />}` pattern. */
function DrawerTitle({ children, className, ...rest }: DrawerTitleProps) {
    return (
        <Text variant="title2" render={<BaseDrawer.Title {...rest} />} {...(className ? { className } : {})}>
            {children}
        </Text>
    );
}

export interface DrawerDescriptionProps {
    readonly children: ReactNode;
    readonly className?: string;
}

function DrawerDescription({ children, className, ...rest }: DrawerDescriptionProps) {
    return (
        <Text
            variant="body"
            color="muted"
            render={<BaseDrawer.Description {...rest} />}
            {...(className ? { className } : {})}
        >
            {children}
        </Text>
    );
}

/**
 * A placeholder glyph — real icons arrive once `Icon`'s own API is decided
 * (`COMPONENTS.md` §13). Kept private to this file rather than shared, for
 * the same reason `Popover.tsx` keeps its own copy instead of exporting one.
 */
function DismissGlyph() {
    return (
        <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
            <path d="M2 2l8 8M10 2l-8 8" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round" />
        </svg>
    );
}

export interface DrawerCloseProps {
    /** The accessible name for the close button — required, per `IconButton`'s own contract. */
    readonly label: string;
    readonly className?: string;
}

/** Composes `IconButton` for the dismiss affordance — see `Popover.Close` for the same pattern. */
function DrawerClose({ label, className }: DrawerCloseProps) {
    return (
        <BaseDrawer.Close
            render={
                <IconButton label={label} tone="ghost" size="sm" {...(className ? { className } : {})}>
                    <DismissGlyph />
                </IconButton>
            }
        />
    );
}

/**
 * Tier 0 — the creation surface. With modals banned (`ARCHITECTURE.md`
 * §12.9), every "new item" flow in the product is this component holding a
 * form. Behaviour — portal, focus trap while open, page-scroll lock,
 * dismiss-on-outside-click, Escape-to-close, focus return to the trigger on
 * close, and swipe-to-dismiss — is entirely Base UI's own
 * (`@base-ui/react/drawer`, ADR-031); load-bearing enough that
 * `Drawer.test.tsx` verifies the focus trap and the return directly rather
 * than assuming Base UI's `modal: true` default delivers them for free.
 */
export const Drawer = {
    Root: DrawerRoot,
    Trigger: DrawerTrigger,
    Popup: DrawerPopup,
    Title: DrawerTitle,
    Description: DrawerDescription,
    Close: DrawerClose,
};
