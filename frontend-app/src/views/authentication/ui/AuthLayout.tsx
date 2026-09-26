"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { Button } from "frontend-shared/ui/button";
import { Logo } from "frontend-shared/ui/logo";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { useTheme } from "frontend-shared/ui/theme";
import { useAuthStrings } from "../../../features/authentication/model/strings";
import { AuthProductPanel } from "../../../widgets/authentication-product-panel";
import styles from "../../../widgets/authentication-step/ui/auth-step.module.css";

export function AuthLayout({ children, security = false }: { children: ReactNode; security?: boolean }) {
    const t = useAuthStrings("auth");
    const { theme, setPreference } = useTheme();
    return <Stack gap="section-gap" className={ security ? styles.securityShell ?? "" : styles.shell ?? "" }>
        <Stack as="section" gap="stack" className={ styles.formPanel ?? "" }>
            <Row as="header" gap="inline" className={ styles.header ?? "" }>
                <Link href={security ? "/" : "/login"} aria-label={t("brand")}><Logo text={t("brand")} /></Link>
                <Button tone="ghost" size="sm" aria-label={t("theme")} onClick={() => { setPreference(theme === "dark" ? "light" : "dark"); }}>{theme === "dark" ? t("light") : t("dark")}</Button>
            </Row>
            <Stack as="main" id="main-content" gap="stack" className={ security ? styles.securityContent ?? "" : styles.content ?? "" }>{children}</Stack>
            <Stack as="footer" gap="inline" className={ styles.footer ?? "" }>{t("footer")}</Stack>
        </Stack>
        {!security && <AuthProductPanel />}
    </Stack>;
}
