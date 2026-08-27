import type { Metadata } from "next";
import { ThemeInitScript, ThemeProvider } from "frontend-shared/ui/theme";
import { enDictionary } from "@/app/i18n";
import { ibmPlexMono, ibmPlexSans } from "./fonts";
import "./globals.css";

export const metadata: Metadata = {
    title: enDictionary.common.productName,
    description: enDictionary.landing.tagline,
};

/**
 * The composition root. Minimal on purpose: it wires the fonts, the theme and
 * the stylesheet, and holds no product surface of its own — that arrives with
 * the route groups.
 */
export default function RootLayout({ children }: { readonly children: React.ReactNode }) {
    return (
        // `suppressHydrationWarning` is required by `ThemeInitScript`, not
        // decorative: the script rewrites this element's class list before React
        // ever gets to compare it against what the server rendered.
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
