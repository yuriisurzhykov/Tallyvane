import type { Metadata } from "next";
import { ThemeInitScript, ThemeProvider } from "frontend-shared/ui/theme";
import { ibmPlexMono, ibmPlexSans } from "./fonts";
import "./globals.css";

export const metadata: Metadata = {
    title: "Tallyvane admin",
    description: "Content administration for Tallyvane.",
    // admin.tallyvane.com sits behind Cloudflare Access (ADR-032) and has no
    // reason to appear in a search index even if that boundary were ever
    // misconfigured — a second, independent reason it should never be found.
    robots: { index: false, follow: false },
};

/**
 * The composition root for this app. Structurally identical to
 * `frontend-web/app/layout.tsx` — same theme wiring, same reasoning for
 * `suppressHydrationWarning` — because it is a different application, not a
 * different design.
 */
export default function RootLayout({ children }: { readonly children: React.ReactNode }) {
    return (
        <html
            lang="en"
            suppressHydrationWarning
            className={`${ibmPlexSans.variable} ${ibmPlexMono.variable}`}
        >
            <head>
                <ThemeInitScript />
            </head>
            <body className="bg-surface-primary text-text-primary">
                <ThemeProvider>{children}</ThemeProvider>
            </body>
        </html>
    );
}
