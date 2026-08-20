/**
 * `apca-w3` ships no typings, and this ambient declaration is scoped to
 * whichever TypeScript program compiles it — `contrast-apca.spec.ts` here
 * imports `test-kit/specs/contrast-apca`, which imports `test-kit`'s own
 * `utils/apca.ts`, pulling it into *this* package's compilation. `test-kit`
 * has its own copy for its own program, and `frontend-web/scripts/apca-w3.d.ts`
 * has one for its own; this is the third and, structurally, the last place
 * one is needed, since nothing consumes `apca-w3` transitively except through
 * this same spec-file path.
 */
declare module "apca-w3" {
    export function calcAPCA(text: string, background: string): number;
    export function fontLookupAPCA(contrast: number): number[];
}
