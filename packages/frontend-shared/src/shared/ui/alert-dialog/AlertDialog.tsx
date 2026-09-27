"use client";

import type { ReactNode } from "react";
import { AlertDialog as BaseAlertDialog } from "@base-ui/react/alert-dialog";
import { Text } from "../text";

const AlertDialogRoot = BaseAlertDialog.Root;
const AlertDialogTrigger = BaseAlertDialog.Trigger;
const AlertDialogClose = BaseAlertDialog.Close;

export type AlertDialogRootProps<Payload = unknown> = BaseAlertDialog.Root.Props<Payload>;
export type AlertDialogTriggerProps<Payload = unknown> = BaseAlertDialog.Trigger.Props<Payload>;
export type AlertDialogCloseProps = BaseAlertDialog.Close.Props;

const FULL_VIEWPORT_STYLE = { position: "fixed", inset: 0 } as const;

export interface AlertDialogPopupOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

export type AlertDialogPopupProps = AlertDialogPopupOwnProps &
    Omit<BaseAlertDialog.Popup.Props, "children" | "className">;

/** Bundles the modal portal, backdrop, viewport and popup into one accessible surface. */
function AlertDialogPopup({ children, className, ...rest }: AlertDialogPopupProps) {
    return (
        <BaseAlertDialog.Portal>
            <BaseAlertDialog.Backdrop
                style={FULL_VIEWPORT_STYLE}
                className="z-scrim bg-surface-overlay transition-popover data-[starting-style]:opacity-0 data-[ending-style]:opacity-0"
            />
            <BaseAlertDialog.Viewport
                style={FULL_VIEWPORT_STYLE}
                className="z-modal flex items-center justify-center overflow-y-auto p-stack"
            >
                <BaseAlertDialog.Popup
                    className={[
                        "w-full max-w-(--ds-component-drawer-width) rounded-card border border-border-subtle bg-surface-elevated p-stack text-body text-text-primary shadow-elevation3 outline-none transition-popover data-[starting-style]:opacity-0 data-[ending-style]:opacity-0",
                        className,
                    ]
                        .filter(Boolean)
                        .join(" ")}
                    {...rest}
                >
                    {children}
                </BaseAlertDialog.Popup>
            </BaseAlertDialog.Viewport>
        </BaseAlertDialog.Portal>
    );
}

export type AlertDialogTitleProps = Omit<BaseAlertDialog.Title.Props, "children" | "className"> & {
    readonly children: ReactNode;
    readonly className?: string;
};

function AlertDialogTitle({ children, className, ...rest }: AlertDialogTitleProps) {
    return (
        <Text
            variant="title2"
            render={<BaseAlertDialog.Title {...rest} />}
            {...(className ? { className } : {})}
        >
            {children}
        </Text>
    );
}

export type AlertDialogDescriptionProps = Omit<BaseAlertDialog.Description.Props, "children" | "className"> & {
    readonly children: ReactNode;
    readonly className?: string;
};

function AlertDialogDescription({ children, className, ...rest }: AlertDialogDescriptionProps) {
    return (
        <Text
            variant="body"
            color="muted"
            render={<BaseAlertDialog.Description {...rest} />}
            {...(className ? { className } : {})}
        >
            {children}
        </Text>
    );
}

export const AlertDialog = {
    Root: AlertDialogRoot,
    Trigger: AlertDialogTrigger,
    Popup: AlertDialogPopup,
    Title: AlertDialogTitle,
    Description: AlertDialogDescription,
    Close: AlertDialogClose,
};
