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
    /** Fluid 36→56px / 700 — public marketing hero, past every heading threshold. */
    "text-hero": LEVEL.heading,
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
 *
 * Both this function and the callback it passes to `page.evaluate` below
 * are exempted from `max-lines-per-function` (see the comment on the
 * `page.evaluate` call). `page.evaluate` serializes that callback to a
 * string and runs it inside the browser page, not in Node — it can close
 * over `knownStyles` (passed explicitly as evaluate's second argument) and
 * nothing else. Every helper inside it has to live in that same closure for
 * exactly that reason: pulling `parse`/`over`/`effectiveBackground`/etc. out
 * to module scope would compile fine but throw a `ReferenceError` at
 * runtime, since a Node-defined function is never part of what gets sent
 * across the page boundary. The length here is that sandboxing constraint,
 * not an unfactored responsibility — narrowly exempted rather than split
 * apart in a way that would break.
 */
// eslint-disable-next-line max-lines-per-function -- see doc comment above
export async function collectTextSamples(page: Page): Promise<TextSample[]> {
    // eslint-disable-next-line max-lines-per-function -- see doc comment above
    return page.evaluate((knownStyles: string[]) => {
        interface Rgba { r: number; g: number; b: number; a: number }

        function parse(value: string): Rgba | null {
            const match = /rgba?\(([^)]+)\)/.exec(value);
            const captured = match?.[1];
            if (captured === undefined) return null;
            const parts = captured.split(/[,\s/]+/).filter(Boolean).map(Number);
            if (parts.length < 3 || parts.slice(0, 3).some(Number.isNaN)) return null;
            const [r, g, b, a] = parts;
            if (r === undefined || g === undefined || b === undefined) return null;
            return { r, g, b, a: a ?? 1 };
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
            // `reduceRight`, not a manual reverse `for` loop: it walks the same
            // last-to-first order without ever indexing `layers` by a computed
            // position, which is what needed a non-null assertion before.
            const opaque: Rgba = { r: 255, g: 255, b: 255, a: 1 };
            return layers.reduceRight((result, layer) => over(layer, result), opaque);
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

        /**
         * `aria-hidden="true"`, on the element or an ancestor, takes it out of
         * the accessibility tree unconditionally — screen readers skip it
         * regardless of what CSS does to it visually. `role="presentation"`/
         * `role="none"` is deliberately NOT included here, even though an
         * earlier version of this function treated it the same way: found the
         * hard way on `Meter`/`Progress`, whose real, visible `Label` element
         * carries `role="presentation"` too — not because its text is inert,
         * but because the value is exposed through the meter/progressbar's
         * own `aria-labelledby` instead, so the label element itself needs no
         * independent role. Excluding every `role="presentation"` element
         * would have skipped that real, on-screen label along with the
         * genuinely inert one below.
         */
        function isAccessibilityHidden(element: Element): boolean {
            return element.closest('[aria-hidden="true"]') !== null;
        }

        const samples: unknown[] = [];
        for (const element of Array.from(document.body.querySelectorAll("*"))) {
            const text = ownText(element);
            if (!text) continue;

            if (isAccessibilityHidden(element)) continue;

            const style = getComputedStyle(element);
            if (style.visibility === "hidden" || style.display === "none" || Number(style.opacity) === 0) continue;

            const rect = element.getBoundingClientRect();
            /**
             * `<= 1`, not `=== 0`: the real bug this widens to catch. Both
             * Tailwind's `sr-only` (`VisuallyHidden`'s own technique) and Base
             * UI's internal announcement-workaround span (found on
             * `Progress`/`Meter` — a `role="presentation"` `<span>x</span>`
             * clipped to a 1×1px box) render at 1×1px rather than 0×0,
             * deliberately, so an old Safari that ignored zero-size elements
             * still picked them up. Contrast is a question about a rendered
             * pixel's colour against its background; a box too small to show
             * any glyph has no such pixel to ask the question about, whether
             * it is clipped via a utility class or an inline style — this
             * check does not care which technique produced the tiny box, only
             * that nothing legible could ever appear inside one.
             */
            if (rect.width <= 1 || rect.height <= 1) continue;

            const background = effectiveBackground(element);
            const rawForeground = parse(style.color);
            if (!rawForeground) continue;
            const foreground = over(rawForeground, background);

            const rgb = (c: Rgba) => `rgb(${String(Math.round(c.r))}, ${String(Math.round(c.g))}, ${String(Math.round(c.b))})`;
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
            `    Lc ${String(finding.lc)}, needs Lc ${String(finding.requiredLc)} (${purpose}, ${String(finding.fontSize)}px/${String(finding.fontWeight)})`,
        ].join("\n");
    });
    return `APCA contrast is insufficient:\n${lines.join("\n")}`;
}
