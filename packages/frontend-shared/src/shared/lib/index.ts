/**
 * Public API of `shared/lib`.
 *
 * Note what this segment is NOT: a home for anything that has no other home.
 * The architecture bans utility dumping grounds outright, so a module landing
 * here needs a reason beyond "it did not fit elsewhere".
 */
export { cn } from "./utils";
export { isSafeRelativePath } from "./safe-relative-path";
export { mergeStyle } from "./merge-style";
export type { BaseUIStyle } from "./merge-style";
export { CONTROL_ICON_CLASS } from "./control-icon-class";
export { DEFAULT_AUTOSAVE_DEBOUNCE_MS, useDebouncedAutosave } from "./use-debounced-autosave";
export type { AutosaveStatus, UseDebouncedAutosaveOptions, UseDebouncedAutosaveResult } from "./use-debounced-autosave";

// registerTallyvaneOtel is deliberately NOT re-exported here — see its own file. This barrel
// pulls in client-only hooks (useDebouncedAutosave), and instrumentation.ts is server-only:
// importing this whole barrel from it drags those hooks into a module graph where Next.js
// refuses them ("You're importing a module that depends on useEffect into a React Server
// Component module"). Measured, not assumed: this repository's own frontend-web build failed
// with exactly that error the first time registerTallyvaneOtel was exported from here instead
// of its own narrow subpath (`frontend-shared/lib/register-otel`).
