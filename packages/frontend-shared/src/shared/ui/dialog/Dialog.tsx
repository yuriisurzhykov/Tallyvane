"use client";

import type { ReactNode } from "react";
import { Dialog as BaseDialog } from "@base-ui/react/dialog";
import { Text } from "../text";

export type DialogRootProps = BaseDialog.Root.Props;

const DialogRoot = BaseDialog.Root;

const FULL_VIEWPORT_STYLE = { position: "fixed", inset: 0 } as const;

export interface DialogPopupProps {
    readonly children: ReactNode;
    readonly className?: string;
}

/** Portal > Backdrop + Viewport > Popup, centred. Focus trap, scroll lock and Escape are Base UI's own. */
function DialogPopup({ children, className }: DialogPopupProps) {
    return (
        <BaseDialog.Portal>
            <BaseDialog.Backdrop
                style={FULL_VIEWPORT_STYLE}
                className="z-scrim bg-surface-overlay transition-popover data-[starting-style]:opacity-0 data-[ending-style]:opacity-0"
            />
            <BaseDialog.Viewport style={FULL_VIEWPORT_STYLE} className="z-modal flex items-center justify-center p-stack">
                <BaseDialog.Popup
                    className={[
                        "flex w-full flex-col gap-stack rounded-card border border-border-subtle bg-surface-elevated p-stack text-body text-text-primary shadow-elevation3 outline-none transition-popover data-[starting-style]:opacity-0 data-[ending-style]:opacity-0",
                        className,
                    ]
                        .filter(Boolean)
                        .join(" ")}
                    style={{ maxWidth: "var(--ds-component-drawer-width)" }}
                >
                    {children}
                </BaseDialog.Popup>
            </BaseDialog.Viewport>
        </BaseDialog.Portal>
    );
}

export interface DialogTextProps {
    readonly children: ReactNode;
}

function DialogTitle({ children }: DialogTextProps) {
    return (
        <Text variant="title2" render={<BaseDialog.Title />}>
            {children}
        </Text>
    );
}

function DialogDescription({ children }: DialogTextProps) {
    return (
        <Text variant="body" color="muted" render={<BaseDialog.Description />}>
            {children}
        </Text>
    );
}

/**
 * Tier 0 — a blocking interruption. `COMPONENTS.md` bans modals for creation flows (those are drawers);
 * this exists for the one thing that cannot wait and is not a form on the page: the session ended
 * under the user's hands (ADR-084, ADR-089). It has no close button on purpose: the caller decides how
 * it ends, by controlling `open`.
 */
export const Dialog = {
    Root: DialogRoot,
    Popup: DialogPopup,
    Title: DialogTitle,
    Description: DialogDescription,
};
