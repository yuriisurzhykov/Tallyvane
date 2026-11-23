import type { AuthorWords } from "@/shared/history";
import { useStrings } from "@/shared/i18n";

export const LOCALE = "en";

/** How the list names whoever made or activated a version. */
export function useAuthorWords(): AuthorWords {
    const t = useStrings("history");
    return { you: t("you"), migration: t("migration"), account: (id) => t("account", { id }) };
}
