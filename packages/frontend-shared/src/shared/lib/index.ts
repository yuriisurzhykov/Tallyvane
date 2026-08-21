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
