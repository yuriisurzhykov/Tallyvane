"use client";

import { Button } from "frontend-shared/ui/button";
import { Menu } from "frontend-shared/ui/menu";
import { useViewer } from "@/entities/viewer";
import { SignOutMenuItem } from "@/features/sign-out";
import { useStrings } from "@/shared/i18n";

/** The signed-in person's name in the top bar, with what can be done as them. */
export function AccountMenu() {
    const t = useStrings("account");
    const viewer = useViewer();
    return (
        <Menu.Root>
            <Menu.Trigger render={<Button tone="ghost" size="sm" aria-label={t("menu")}>{viewer.label()}</Button>} />
            <Menu.Popup align="end">
                <SignOutMenuItem />
            </Menu.Popup>
        </Menu.Root>
    );
}
