import { Button } from "frontend-shared/ui/button";
import { Logo } from "frontend-shared/ui/logo";
import { Row } from "frontend-shared/ui/row";
import { useTheme } from "frontend-shared/ui/theme";
import type { useAdminLoginStrings } from "@/features/admin-login";

type Translate = ReturnType<typeof useAdminLoginStrings>;

export function AdminLoginHeader({ t }: { readonly t: Translate }) {
    const { theme, setPreference } = useTheme();
    return <Row as="header" gap="inline" className="justify-between">
        <Logo text={t("brand")} />
        <Button
            tone="ghost"
            size="sm"
            aria-label={theme === "dark" ? t("lightTheme") : t("darkTheme")}
            onClick={() => { setPreference(theme === "dark" ? "light" : "dark"); }}
        >
            {theme === "dark" ? t("light") : t("dark")}
        </Button>
    </Row>;
}
