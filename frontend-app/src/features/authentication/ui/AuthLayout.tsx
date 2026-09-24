"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { Button } from "frontend-shared/ui/button";
import { Logo } from "frontend-shared/ui/logo";
import { useTheme } from "frontend-shared/ui/theme";
import { useAuthStrings } from "../model/strings";
import styles from "./auth.module.css";

export function AuthLayout({ children }: { children: ReactNode }) {
    const t = useAuthStrings("auth");
    const { theme, setPreference } = useTheme();
    return <div className={styles.shell}>
        <section className={styles.formPanel}>
            <header className={styles.header}>
                <Link href="/login" aria-label={t("brand")}><Logo text={t("brand")} /></Link>
                <Button tone="ghost" size="sm" aria-label={t("theme")} onClick={() => setPreference(theme === "dark" ? "light" : "dark")}>{theme === "dark" ? t("light") : t("dark")}</Button>
            </header>
            <main id="main-content" className={styles.content}>{children}</main>
            <footer className={styles.footer}>{t("footer")}</footer>
        </section>
        <aside className={styles.productPanel} aria-label={t("preview")}>
            <div className={styles.productCopy}>
                <p className="text-overline text-text-muted">{t("productEyebrow")}</p>
                <h2 className={styles.productTitle}>{t("productTitle")}</h2>
                <p className="text-body text-text-secondary">{t("productDescription")}</p>
            </div>
            <div className={styles.preview}>
                <div className={styles.previewHeader}><span className="text-title3">{t("today")}</span><span className={styles.previewMark} aria-hidden="true">↗</span></div>
                <div className={styles.taskCard}>
                    <span className="text-overline text-text-muted">{t("nextStep")}</span>
                    <h3 className="text-body-strong">{t("previewTask")}</h3><p className="text-small text-text-muted">{t("previewDetail")}</p>
                </div>
                <p className="text-small text-text-muted">{t("pipeline")}</p>
                <div className={styles.pipeline}><span>{t("saved")}</span><span>{t("applied")}</span><span>{t("interview")}</span></div>
                <div className={styles.roleCard}><span className={styles.companyMark} aria-hidden="true">↗</span><div><p className="text-body-strong">{t("role")}</p><p className="text-small text-text-muted">{t("company")}</p></div><span className={styles.statusDot} aria-hidden="true" /></div>
            </div>
            <p className={styles.previewCaption}>{t("previewLabel")}</p>
        </aside>
    </div>;
}
