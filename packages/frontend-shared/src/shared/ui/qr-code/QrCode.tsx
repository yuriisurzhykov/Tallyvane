import { create } from "qrcode";
import { THEME_CLASS } from "../theme/provider/constants";

export interface QrCodeProps {
    /** What the code carries. Drawn in the browser, so it never leaves it. */
    readonly value: string;
    /** What the picture means for someone who cannot see it, as there is no text in it. */
    readonly label: string;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/** The empty border a reader needs around the code to find its edges, in modules (the specification asks for four). */
const QUIET_ZONE = 4;

/**
 * How big it is drawn: `--ds-component-qr-code-size`, and never wider than its container. An SVG that has only a
 * `viewBox` fills the width it is given, which is what a bare one did on a wide page.
 */
const SIZE_CLASS = "w-(--ds-component-qr-code-size) max-w-full aspect-square";

/**
 * One path of unit squares, one per dark module. Rows are read left to right, and each `M x y h1 v1 h-1 z`
 * draws a square from its corner, so the whole code is one element and not a few hundred.
 */
function squares(size: number, data: Uint8Array): string {
    const parts: string[] = [];
    for (let row = 0; row < size; row += 1) {
        for (let column = 0; column < size; column += 1) {
            if (data[row * size + column] === 1) {
                parts.push(`M${String(column + QUIET_ZONE)} ${String(row + QUIET_ZONE)}h1v1h-1z`);
            }
        }
    }
    return parts.join("");
}

/**
 * Tier 0 — a QR code, drawn as an SVG from the `qrcode` library's matrix (no canvas, no image, no HTML
 * string, so it renders on the server and in tests alike).
 *
 * It is always dark on light, whatever the page's theme: many readers cannot scan a code whose colours are
 * inverted, and a dark theme would invert it. So the element wears the light theme's own roles (the same
 * `theme-light` class the theme provider uses) for its two colours, and nothing in it is a literal colour.
 */
export function QrCode({ value, label, className }: QrCodeProps) {
    const { size, data } = create(value, { errorCorrectionLevel: "M" }).modules;
    const side = size + 2 * QUIET_ZONE;
    return (
        <svg
            role="img"
            aria-label={label}
            viewBox={`0 0 ${String(side)} ${String(side)}`}
            shapeRendering="crispEdges"
            className={[THEME_CLASS.light, SIZE_CLASS, "bg-surface-primary text-text-primary rounded-control", className].filter(Boolean).join(" ")}
        >
            <path d={squares(size, data)} fill="currentColor" />
        </svg>
    );
}
