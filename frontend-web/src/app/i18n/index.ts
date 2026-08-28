import enDictionary from "./locales/en.json" with { type: "json" };
import { createUseStrings } from "frontend-shared/i18n";

/**
 * App-owned instantiation of the shared lookup factory. The dictionary lives
 * here, not in `frontend-shared`, so shared stays free of product vocabulary
 * (ADR-032 / ARCHITECTURE.md §13.2).
 *
 * `enDictionary` is exported for module-scope readers (route metadata) that
 * cannot call `useStrings` — the function is named like a hook, and
 * `react-hooks/rules-of-hooks` flags any top-level call even though it is
 * not a React hook.
 */
export const useStrings = createUseStrings(enDictionary);
export { enDictionary };
