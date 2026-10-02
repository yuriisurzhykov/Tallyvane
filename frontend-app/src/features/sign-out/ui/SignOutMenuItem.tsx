"use client";

import { useApi } from "frontend-shared/api";
import { Menu } from "frontend-shared/ui/menu";
import { useStrings } from "@/shared/i18n";

/** Ends the session on the server (always answered with 204, even without one) and leaves for the sign-in page. */
export function SignOutMenuItem() {
    const t = useStrings("account");
    const api = useApi();

    const signOut = async () => {
        await api.delete("/session");
        window.location.replace("/login");
    };

    return <Menu.Item onClick={() => void signOut()}>{t("signOut")}</Menu.Item>;
}
