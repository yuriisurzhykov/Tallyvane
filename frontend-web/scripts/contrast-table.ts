/**
 * Prints which steps of the neutral scale are actually usable as text on each
 * surface, measured both ways.
 *
 * It exists because the palette was assigned by eye — roles were pointed at
 * steps that looked like a sensible descent — and both contrast suites then
 * disagreed. Choosing from a measured table turns the next attempt from taste
 * into selection: the question stops being "does this look about right" and
 * becomes "which of these is dark enough".
 *
 * Surfaces come from the generated, already-resolved theme data, so the table
 * can never describe a palette other than the one that ships. Candidates come
 * from the primitive scale, because that is what a role is allowed to point at.
 *
 * Read-only: it changes nothing and is not part of any check. Run it while
 * tuning, throw the output away.
 *
 *   pnpm --filter tallyvane-frontend exec tsx scripts/contrast-table.ts
 */
import { calcAPCA, fontLookupAPCA } from "apca-w3";
import { hslStringToRgb01, hslStringToRgbString } from "design-token-engine";
import { color } from "frontend-shared/ui/theme/tokens";
import { resolved } from "frontend-shared/ui/theme";

/** The two sizes this interface actually sets small text at: dense tables and metadata, and body copy. */
const REFERENCE_SIZES = [13, 16] as const;
const WEIGHT = 400;

/** WCAG 2.2 AA for text below the large-text threshold, which both reference sizes are. */
const WCAG_AA = 4.5;

/** sRGB relative luminance, per WCAG 2's own definition — the piecewise gamma curve, not a plain power. */
function relativeLuminance(hsl: string): number {
    const linear = hslStringToRgb01(hsl).map((channel) =>
        channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4,
    );
    return 0.2126 * linear[0]! + 0.7152 * linear[1]! + 0.0722 * linear[2]!;
}

function wcagRatio(foreground: string, background: string): number {
    const a = relativeLuminance(foreground);
    const b = relativeLuminance(background);
    const [lighter, darker] = a > b ? [a, b] : [b, a];
    return (lighter + 0.05) / (darker + 0.05);
}

/** The smallest size APCA accepts at this contrast and weight. `Infinity` when no size does. */
function apcaMinimumSize(foreground: string, background: string): { lc: number; minSize: number } {
    const lc = Math.abs(calcAPCA(hslStringToRgbString(foreground), hslStringToRgbString(background)));
    const minSize = fontLookupAPCA(lc)[Math.round(WEIGHT / 100)] ?? Infinity;
    // The lookup uses out-of-range values as sentinels for "nothing works".
    return { lc, minSize: minSize > 0 && minSize < 200 ? minSize : Infinity };
}

function verdict(passes: boolean): string {
    return passes ? "yes" : " · ";
}

function table(title: string, background: string, candidates: [string, string][]): void {
    console.log(`\n${ title }  —  background ${ background }`);
    console.log("  step          colour                Lc    APCA min   ratio   APCA 13  APCA 16  AA");
    console.log("  " + "─".repeat(86));

    for (const [name, hsl] of candidates) {
        const { lc, minSize } = apcaMinimumSize(hsl, background);
        const ratio = wcagRatio(hsl, background);
        const min = minSize === Infinity ? "never" : `${ minSize }px`;

        console.log(
            "  " +
            name.padEnd(14) +
            hsl.padEnd(22) +
            lc.toFixed(1).padStart(5) +
            min.padStart(11) +
            ratio.toFixed(2).padStart(8) +
            verdict(minSize <= REFERENCE_SIZES[0]).padStart(9) +
            verdict(minSize <= REFERENCE_SIZES[1]).padStart(9) +
            verdict(ratio >= WCAG_AA).padStart(5),
        );
    }
}

const neutrals = Object.entries(color.neutral as Record<string, string>).map(
    ([step, hsl]) => [`neutral.${ step }`, hsl] as [string, string],
);

/**
 * Text on a page, on a card, and on the inset wells inside them — the three
 * grounds any body text can land on. A role has to clear the darkest of them,
 * so all three are printed rather than only the page.
 */
for (const theme of ["dark", "light"] as const) {
    const roles = resolved[theme].color as Record<string, string>;
    for (const surface of ["surfacePrimary", "surfaceElevated", "surfaceInset"] as const) {
        table(`${ theme } · ${ surface }`, roles[surface]!, neutrals);
    }
}

/**
 * The status fills are the other half of the problem, and a different question:
 * not which neutral reads on them, but whether either extreme does. A fill in
 * the middle of the lightness range answers "neither", which is why these
 * currently fail — too light for white text, too dark for black.
 */
const extremes = neutrals.filter(([name]) => name === "neutral.0" || name === "neutral.1000");

console.log("\n\n=== Text on a solid status fill ===");
for (const status of ["statusSuccess", "statusDanger", "statusAttention", "statusInfo"] as const) {
    // Shared between themes, so measuring one is measuring both.
    table(status, (resolved.dark.color as Record<string, string>)[status]!, extremes);
}

/**
 * The other axis, and the one the surface tables cannot show.
 *
 * Every column above is computed at weight 400, and at that weight APCA sets a
 * floor that no amount of contrast reaches under: even the theoretical maximum
 * needs about fifteen pixels. So a table of colours answers "which colour" while
 * quietly assuming the size and weight are already viable — and if they are not,
 * every row fails and the colours look like the culprit.
 *
 * This prints the same lookup along the weight axis instead: at a given
 * contrast, how small the text may be at each weight.
 */
console.log("\n\n=== Smallest usable size, by weight ===");
console.log("Lc is what the colour pair achieves; the row shows what size each weight then allows.\n");

const WEIGHTS = [300, 400, 500, 600, 700] as const;
const SAMPLE_CONTRASTS = [100, 90, 75, 60, 45] as const;

console.log("     Lc  " + WEIGHTS.map((w) => String(w).padStart(9)).join(""));
console.log("  " + "─".repeat(53));
for (const lc of SAMPLE_CONTRASTS) {
    const lookup = fontLookupAPCA(lc);
    const cells = WEIGHTS.map((weight) => {
        const size = lookup[weight / 100] ?? Infinity;
        return (size > 0 && size < 200 ? `${ size }px` : "never").padStart(9);
    });
    console.log(String(lc).padStart(7) + "  " + cells.join(""));
}
