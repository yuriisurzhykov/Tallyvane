import { IBM_Plex_Mono, IBM_Plex_Sans } from "next/font/google";

/**
 * Identical to `frontend-web/app/fonts.ts` and `frontend-admin/app/fonts.ts` —
 * same brand, same typeface, same reasoning for every option below.
 * Duplicated rather than shared because `next/font/google` must be called
 * from within the Next.js app that renders it; there is no package boundary
 * that would let this live in `frontend-shared` instead.
 */
export const ibmPlexSans = IBM_Plex_Sans({
    subsets: ["latin"],
    weight: "variable",
    display: "optional",
    variable: "--font-ibm-plex-sans",
});

export const ibmPlexMono = IBM_Plex_Mono({
    subsets: ["latin"],
    weight: ["400"],
    display: "optional",
    variable: "--font-ibm-plex-mono",
});
