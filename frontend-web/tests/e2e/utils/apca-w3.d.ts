/**
 * `apca-w3` ships no typings. Only the two functions this suite uses are
 * declared, rather than a speculative shape for the rest of its surface.
 */
declare module "apca-w3" {
    /**
     * Lightness contrast, signed: positive for dark text on a light background,
     * negative for light on dark. The sign carries meaning in APCA — the same
     * pair is not equally readable in both directions — so it is never dropped
     * before the magnitude is taken deliberately.
     */
    export function calcAPCA(text: string, background: string): number;

    /**
     * The official size-and-weight lookup: given an Lc, the minimum font size
     * in pixels for each weight, indexed by weight / 100. Sentinel values above
     * the plausible size range mean no size at that weight is acceptable.
     */
    export function fontLookupAPCA(contrast: number): number[];
}
