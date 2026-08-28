import type { Metadata } from "next";
import { ThemeInitScript, ThemeProvider } from "frontend-shared/ui/theme";
import { ibmPlexMono, ibmPlexSans } from "./fonts";
import "./globals.css";

export const metadata: Metadata = {
    title: "Tallyvane",
    description: "The job-search console.",
    // Sits behind a per-hostname session cookie checked by Ktor (ADR-032), not
    // Cloudflare Access like admin — but it is still a private dashboard with
    // no reason to appear in a search index.
    robots: { index: false, follow: false },
};

/**
 * The composition root for this app. Structurally identical to
 * `frontend-web/app/layout.tsx` and `frontend-admin/app/layout.tsx` — same
 * theme wiring, same reasoning for `suppressHydrationWarning` — because it is
 * a different application, not a different design.
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
