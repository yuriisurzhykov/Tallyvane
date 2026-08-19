import { IBM_Plex_Mono, IBM_Plex_Sans } from "next/font/google";

/**
 * Self-hosted: Next downloads these at build time and serves them from our own
 * origin, so there is no runtime request to fonts.googleapis.com and no
 * render-blocking hop to a third party.
 *
 * IMPORTANT — a font defined here does nothing until `.variable` is applied to
 * `<html>` in the root layout. Until then `tokens/typography.ts`'s
 * `var(--font-ibm-plex-sans)` resolves to nothing and every page silently
 * renders in the `system-ui` fallback, with no error anywhere to say so. This
 * is a known, documented failure mode of exactly this setup, not a
 * hypothetical.
 *
 * Only `latin` is subsetted. The interface ships in English today; adding
 * `cyrillic` is a one-word change the day a Russian locale does, and declaring
 * it now would preload a file nothing reads.
 *
 * `display: "optional"` rather than the more usual `"swap"`. The two differ in
 * what happens when the font is not ready in time: `"swap"` will replace the
 * fallback whenever the real font eventually arrives, which shifts the layout
 * at an unpredictable moment; `"optional"` gives it roughly 100ms and then
 * commits to the fallback for the rest of that navigation. Because these are
 * self-hosted and preloaded there is no CDN latency to hedge against, so the
 * font is almost always ready inside that window — and Next generates a
 * metric-matched fallback face, so the rare miss costs very little visually
 * while removing font-swap layout shift entirely. The trade-off is real and
 * deliberate: a cold first visit on a slow connection may render the whole page
 * in the fallback.
 */

/**
 * A genuine variable font — the generated `next/font/google` signature offers
 * `weight: "variable"` and covers 100 to 700. One file spans the whole range
 * `typography.weight` uses, instead of four static-weight files.
 *
 * 700 is also the ceiling, which is why `typography.weight` stops there. Asking
 * for 800 or 900 does not fail; the browser synthesises the difference by
 * smearing the glyphs, and the result looks subtly wrong in a way that is hard
 * to attribute.
 */
export const ibmPlexSans = IBM_Plex_Sans({
    subsets: ["latin"],
    weight: "variable",
    display: "optional",
    variable: "--font-ibm-plex-sans",
});

/**
 * Mono is NOT variable, unlike its sans counterpart — checked against the same
 * generated signature, where `weight` is required and has no `"variable"`
 * member. Each weight is therefore its own file, so only the one the interface
 * actually uses is loaded: `textStyles.numeric` is the sole consumer and sets
 * regular. A second weight here costs a second download and needs a reason.
 */
export const ibmPlexMono = IBM_Plex_Mono({
    subsets: ["latin"],
    weight: ["400"],
    display: "optional",
    variable: "--font-ibm-plex-mono",
});
