import type { Page } from "@playwright/test";
import { calcAPCA, fontLookupAPCA } from "apca-w3";

export interface TextSample {
    readonly selector: string;
    readonly text: string;
    readonly foreground: string;
    readonly background: string;
    readonly fontSize: number;
    readonly fontWeight: number;
}

export interface ContrastFinding extends TextSample {
    readonly lc: number;
    readonly requiredSize: number;
}

/**
 * Collects every visible run of text on the page together with the colour it is
 * actually drawn against.
 *
 * "Actually" is the hard part and the reason this cannot be checked against the
 * token pairs alone. An element's own background is usually transparent, and
 * several of this system's surface roles are translucent overlays, so the
 * colour behind a word is the composite of every background between it and the
 * page. That is resolved here by walking the ancestor chain and alpha-blending
 * from the bottom up.
 *
 * Known limits, stated rather than hidden: background images, gradients and
 * blend modes are not accounted for, and neither is `opacity` on an ancestor.
 * None of those appear in this design — it has no gradients by decision — but a
 * page that grows one will be measured optimistically until this grows with it.
 */
export async function collectTextSamples(page: Page): Promise<TextSample[]> {
    return page.evaluate(() => {
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
            // The canvas beneath everything. White is the browser's own default
            // and only shows through if the page set no background at all,
            // which would itself be worth failing on.
            let result: Rgba = { r: 255, g: 255, b: 255, a: 1 };
            for (let i = layers.length - 1; i >= 0; i -= 1) result = over(layers[i]!, result);
            return result;
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

        const samples: TextSample[] = [];
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
            // Text can be translucent too, so it is composited over the
            // background it sits on rather than measured as if it were opaque.
            const foreground = over(rawForeground, background);

            const rgb = (c: Rgba) => `rgb(${Math.round(c.r)}, ${Math.round(c.g)}, ${Math.round(c.b)})`;
            samples.push({
                selector: describe(element),
                text: text.slice(0, 60),
                foreground: rgb(foreground),
                background: rgb(background),
                fontSize: Number.parseFloat(style.fontSize),
                fontWeight: Number.parseInt(style.fontWeight, 10) || 400,
            });
        }
        return samples;

        // The interface is re-declared inside the browser context because this
        // function is serialised and evaluated there, with no access to the
        // module's types.
        interface TextSample {
            selector: string;
            text: string;
            foreground: string;
            background: string;
            fontSize: number;
            fontWeight: number;
        }
    });
}

/**
 * Sizes above this are not real text; the lookup uses out-of-range values as
 * sentinels for "no size at this weight is acceptable".
 */
const IMPOSSIBLE_SIZE = 200;

/**
 * Judges a sample against APCA.
 *
 * APCA has no single pass mark, and that is the substantive difference from the
 * ratio it replaces. Readability depends on how large and how heavy the text is
 * as much as on the two colours, so the algorithm answers "what is the smallest
 * size that works at this contrast and weight" and the check is whether the
 * text is at least that big. The lookup table is the algorithm's own — inventing
 * thresholds here would be both wrong and, under its licence, not APCA.
 */
export function judge(sample: TextSample): ContrastFinding | null {
    const lc = Math.abs(calcAPCA(sample.foreground, sample.background));
    const weightIndex = Math.min(9, Math.max(1, Math.round(sample.fontWeight / 100)));
    const requiredSize = fontLookupAPCA(lc)[weightIndex] ?? IMPOSSIBLE_SIZE;

    const achievable = requiredSize > 0 && requiredSize < IMPOSSIBLE_SIZE;
    if (achievable && sample.fontSize >= requiredSize) return null;

    return { ...sample, lc: Math.round(lc * 10) / 10, requiredSize };
}

export function formatFindings(findings: readonly ContrastFinding[]): string {
    if (findings.length === 0) return "";
    const lines = findings.map((finding) => {
        const required = finding.requiredSize >= IMPOSSIBLE_SIZE
            ? "no size is sufficient at this weight"
            : `needs ${finding.requiredSize}px, is ${finding.fontSize}px`;
        return [
            `- ${finding.selector}`,
            `    "${finding.text}"`,
            `    ${finding.foreground} on ${finding.background} — Lc ${finding.lc} at weight ${finding.fontWeight}`,
            `    ${required}`,
        ].join("\n");
    });
    return `APCA contrast is insufficient:\n${lines.join("\n")}`;
}
