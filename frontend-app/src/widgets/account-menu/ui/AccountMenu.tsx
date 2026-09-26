"use client";

import { Fragment, useState } from "react";
import Link from "next/link";
import { authClient } from "@/features/authentication/api/client";
import { useAuthStrings } from "@/features/authentication/model/strings";
import { Button } from "frontend-shared/ui/button";
import { Menu } from "frontend-shared/ui/menu";
import { useToast } from "frontend-shared/ui/toast";

type AccountMenuItem =
    | { readonly id: string; readonly kind: "link"; readonly label: string; readonly href: string }
    | { readonly id: string; readonly kind: "action"; readonly label: string; readonly onSelect: () => void; readonly disabled?: boolean };

export function AccountMenu() {
    const t = useAuthStrings("auth");
    const { actions: toast } = useToast();
    const [signingOut, setSigningOut] = useState(false);

    async function signOut() {
        if (signingOut) return;
        setSigningOut(true);
        try {
            await authClient.signOut();
        } catch {
            toast.add({ title: t("signOutFailed"), tone: "danger" });
            setSigningOut(false);
        }
    }

    const items: readonly AccountMenuItem[] = [
        { id: "account-settings", kind: "link", label: t("accountSettings"), href: "/account/security" },
        { id: "sign-out", kind: "action", label: t("logout"), onSelect: () => { void signOut(); }, disabled: signingOut },
    ];

    return (
        <Menu.Root>
            <Menu.Trigger render={
                <Button
                    type="button"
                    tone="ghost"
                    aria-label={t("accountMenu")}
                    trailingIcon={<span aria-hidden="true">⌄</span>}
                >
                    {t("accountMenu")}
                </Button>
            } />
            <Menu.Popup>
                {items.map((item, index) => (
                    <Fragment key={item.id}>
                        {index > 0 ? <Menu.Separator /> : null}
                        {item.kind === "link" ? (
                            <Menu.Item render={<Link href={item.href} />}>{item.label}</Menu.Item>
                        ) : (
                            <Menu.Item onClick={item.onSelect} disabled={item.disabled}>{item.label}</Menu.Item>
                        )}
                    </Fragment>
                ))}
            </Menu.Popup>
        </Menu.Root>
    );
}
