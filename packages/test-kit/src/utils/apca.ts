import type { Page } from "@playwright/test";
import { calcAPCA } from "apca-w3";

/**
 * APCA's use-case levels, quoted from the algorithm's own documentation.
 *
 * These are the primary way the method is meant to be applied. The font lookup
 * table — which an earlier version of this file used for everything — is
 * described by its authors as OPTIONAL, and encodes the strict reading for
 * columns of body text. Applying it to a badge or an overline asks continuous
 * reading of text nobody reads continuously, and produces a size floor the
 * method does not actually impose.
 */
const LEVEL = {
    /** Body text, and any non-body text below 12px. Nothing here is lower than the strictest level available. */
    body: 90,
    /** Body text at 18px/400 and up, or non-body from 15px/400. */
    prominent: 75,
    /** Content text that is not body, column or block text — text you want read, but not read through. */
    content: 60,
    /** Larger, heavier text: headlines from 36px/400 or 24px/700. */
    heading: 45,
    /** Spot-readable only: placeholder text, disabled controls, a copyright line. */
    spot: 30,
} as const;

/**
 * The level each text style is held to, derived from its size and weight
 * against the documented thresholds.
 *
 * Keyed by the utility class, because that is what the DOM carries and what a
 * component actually writes. A style missing from this table fails the suite
 * rather than falling through to a default — an unclassified style is one
 * nobody decided the purpose of, and guessing on its behalf is how a heading
 * threshold ends up applied to body copy.
 *
 * Every entry rests on the shared type scale in `frontend-shared`, which is
 * why this table is safe to reuse across every consumer of that theme rather
 * than belonging to whichever app happened to write it first.
 */
const STYLE_LEVEL: Readonly<Record<string, number>> = {
    /** 36px/600 — a headline by any reading. */
    "text-display": LEVEL.heading,
    /** 28, 24 and 20px at weight 600, all past the 18px/600 mark. */
    "text-title1": LEVEL.content,
    "text-title2": LEVEL.content,
    "text-title3": LEVEL.content,
    /** 18px/400 body: exactly the size at which the body minimum becomes Lc 75. */
    "text-body": LEVEL.prominent,
    "text-body-strong": LEVEL.prominent,
    /** 17, 16 and 15px non-body: all at or above the 15px mark for Lc 75. */
    "text-small": LEVEL.prominent,
    "text-caption": LEVEL.prominent,
    "text-overline": LEVEL.prominent,
    "text-numeric": LEVEL.prominent,
};

export interface TextSample {
    readonly selector: string;
    readonly text: string;
    readonly foreground: string;
    readonly background: string;
    readonly fontSize: number;
    readonly fontWeight: number;
    /** The nearest text style in effect, inherited like font-size. `null` when no ancestor declares one. */
    readonly style: string | null;
    /** Inside a disabled control, or placeholder text. Held to the spot-readable level, per the documentation's own listing of those two cases. */
    readonly spotReadable: boolean;
}

export interface ContrastFinding extends TextSample {
    readonly lc: number;
    readonly requiredLc: number;
}

/**
 * Collects every visible run of text together with the colour it is actually
 * drawn against.
 *
 * "Actually" is the hard part, and the reason this cannot be checked against
 * token pairs alone: an element's own background is usually transparent and
 * several surface roles are translucent overlays, so the colour behind a word
 * is the composite of every background between it and the page.
 *
 * Known limits, stated rather than hidden: background images, gradients, blend
 * modes and ancestor `opacity` are not accounted for. None appear in this
 * design, but a page that grows one will be measured optimistically.
 */
export async function collectTextSamples(page: Page): Promise<TextSample[]> {
    return page.evaluate((knownStyles: string[]) => {
        interface Rgba { r: number; g: number; b: number; a: number }

        function parse(value: string): Rgba | null {
            const match = /rgba?\(([^)]+)\)/.exec(value);
            if (!match) return null;
            const parts = match[1]!.split(/[,\s/]+/).filter(Boolean).map(Number);
            if (parts.length < 3 || parts.slice(0, 3).some(Number.isNaN)) return null;
            return { r: parts[0]!, g: parts[1]!, b: parts[2]!, a: parts.length > 3 ? parts[3]! : 1 };
        }

        /** Standard source-over compositing. */
        function over(top: Rgba, bottom: Rgba): Rgba {
            const a = top.a + bottom.a * (1 - top.a);
            if (a === 0) return { r: 0, g: 0, b: 0, a: 0 };
            const mix = (t: number, b: number) => (t * top.a + b * bottom.a * (1 - top.a)) / a;
            return { r: mix(top.r, bottom.r), g: mix(top.g, bottom.g), b: mix(top.b, bottom.b), a };
        }

        function effectiveBackground(element: Element): Rgba {
            const layers: Rgba[] = [];
            for (let node: Element | null = element; node; node = node.parentElement) {
                const colour = parse(getComputedStyle(node).backgroundColor);
                if (colour && colour.a > 0) layers.push(colour);
            }
            let result: Rgba = { r: 255, g: 255, b: 255, a: 1 };
            for (let i = layers.length - 1; i >= 0; i -= 1) result = over(layers[i]!, result);
            return result;
        }

        /**
         * The style in effect, found by walking up — font size inherits, so the
         * style governing a word may be declared on an ancestor.
         *
         * Matched against the known list rather than by prefix: colour
         * utilities are also called `text-*` (`text-text-muted`,
         * `text-status-danger-text`), and a prefix match would take the first
         * of those as the style.
         */
        function effectiveStyle(element: Element): string | null {
            for (let node: Element | null = element; node; node = node.parentElement) {
                const current = node;
                const found = knownStyles.find((style) => current.classList.contains(style));
                if (found) return found;
            }
            return null;
        }

        function isSpotReadable(element: Element): boolean {
            return element.closest("[disabled], [aria-disabled='true'], fieldset[disabled]") !== null;
        }

        function describe(element: Element): string {
            const parts: string[] = [];
            for (let node: Element | null = element; node && parts.length < 3; node = node.parentElement) {
                const id = node.id ? `#${node.id}` : "";
                const testId = node.getAttribute("data-testid");
                parts.unshift(testId ? `[data-testid="${testId}"]` : `${node.tagName.toLowerCase()}${id}`);
                if (id || testId) break;
            }
            return parts.join(" > ");
        }

        function ownText(element: Element): string {
            return Array.from(element.childNodes)
                .filter((node) => node.nodeType === Node.TEXT_NODE)
                .map((node) => node.textContent ?? "")
                .join(" ")
                .replace(/\s+/g, " ")
                .trim();
        }

        const samples: unknown[] = [];
        for (const element of Array.from(document.body.querySelectorAll("*"))) {
            const text = ownText(element);
            if (!text) continue;

            const style = getComputedStyle(element);
            if (style.visibility === "hidden" || style.display === "none" || Number(style.opacity) === 0) continue;

            const rect = element.getBoundingClientRect();
            if (rect.width === 0 || rect.height === 0) continue;

            const background = effectiveBackground(element);
            const rawForeground = parse(style.color);
            if (!rawForeground) continue;
            const foreground = over(rawForeground, background);

            const rgb = (c: Rgba) => `rgb(${Math.round(c.r)}, ${Math.round(c.g)}, ${Math.round(c.b)})`;
            samples.push({
                selector: describe(element),
                text: text.slice(0, 60),
                foreground: rgb(foreground),
                background: rgb(background),
                fontSize: Number.parseFloat(style.fontSize),
                fontWeight: Number.parseInt(style.fontWeight, 10) || 400,
                style: effectiveStyle(element),
                spotReadable: isSpotReadable(element),
            });
        }
        return samples as never;
    }, Object.keys(STYLE_LEVEL));
}

/** The level a sample is held to, or `null` when its purpose was never declared. */
function requiredLevel(sample: TextSample): number | null {
    if (sample.spotReadable) return LEVEL.spot;
    if (sample.style === null) return null;
    return STYLE_LEVEL[sample.style] ?? null;
}

export function judge(sample: TextSample): ContrastFinding | null {
    const required = requiredLevel(sample);
    // An undeclared purpose is reported as a failure at the strictest level:
    // silently passing text nobody classified is how the check stops covering
    // the page it is pointed at.
    const threshold = required ?? LEVEL.body;

    const lc = Math.abs(calcAPCA(sample.foreground, sample.background));
    if (required !== null && lc >= threshold) return null;

    return { ...sample, lc: Math.round(lc * 10) / 10, requiredLc: threshold };
}

export function formatFindings(findings: readonly ContrastFinding[]): string {
    if (findings.length === 0) return "";
    const lines = findings.map((finding) => {
        const purpose = finding.spotReadable
            ? "spot-readable"
            : finding.style ?? "NO TEXT STYLE — classify it, or the level is a guess";
        return [
            `- ${finding.selector}`,
            `    "${finding.text}"`,
            `    ${finding.foreground} on ${finding.background}`,
            `    Lc ${finding.lc}, needs Lc ${finding.requiredLc} (${purpose}, ${finding.fontSize}px/${finding.fontWeight})`,
        ].join("\n");
    });
    return `APCA contrast is insufficient:\n${lines.join("\n")}`;
}
