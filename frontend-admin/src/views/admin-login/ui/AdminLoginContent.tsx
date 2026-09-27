import { AdminMfaPanel } from "./AdminMfaPanel";
import { AdminPasswordPanel } from "./AdminPasswordPanel";
import { AdminStatusPanel } from "./AdminStatusPanel";
import type { AdminLoginController } from "../model/useAdminLoginController";

export function AdminLoginContent({ controller }: { readonly controller: AdminLoginController }) {
    const { screen } = controller.state;
    if (screen === "password") return <AdminPasswordPanel controller={controller} />;
    if (screen === "mfa") return <AdminMfaPanel controller={controller} />;
    return <AdminStatusPanel controller={controller} />;
}
