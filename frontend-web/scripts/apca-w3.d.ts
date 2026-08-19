/**
 * `apca-w3` ships no typings. Only the two functions `contrast-table.ts` uses
 * are declared, rather than a speculative shape for the rest of its surface.
 *
 * Duplicated in `packages/test-kit/src/utils/apca-w3.d.ts`, deliberately: an
 * ambient module declaration is scoped to the TypeScript program it compiles
 * in, and this script is a different program from `test-kit`'s. It is a type
 * declaration for a third-party package, not shared logic, so a second copy
 * here costs nothing to keep in step.
 */
declare module "apca-w3" {
    export function calcAPCA(text: string, background: string): number;
    export function fontLookupAPCA(contrast: number): number[];
}
