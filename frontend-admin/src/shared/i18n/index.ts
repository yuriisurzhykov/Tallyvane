import enDictionary from "./locales/en.json" with { type: "json" };
import { createUseStrings } from "frontend-shared/i18n";

/** App-owned instantiation of the shared lookup factory; see `frontend-web/src/app/i18n/index.ts`. */
export const useStrings = createUseStrings(enDictionary);
export { enDictionary };
