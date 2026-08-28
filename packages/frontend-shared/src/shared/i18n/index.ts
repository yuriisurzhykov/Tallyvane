export type KeyOf<TNamespace extends Record<string, string>> = keyof TNamespace & string;

/**
 * Builds a typed `namespace → key → string` lookup against a dictionary the
 * caller owns. Shared ships the mechanism, never the copy: each app passes in
 * its own JSON so this package stays free of product vocabulary (ADR-032).
 *
 * Not a React hook — the `useStrings` name matches ARCHITECTURE.md §13 and
 * ADR-016. It closes over the dictionary; there is no context, no CMS merge,
 * and nothing here that needs a client boundary.
 */
export function createUseStrings<TDict extends Record<string, Record<string, string>>>(dictionary: TDict) {
    return function useStrings<N extends keyof TDict & string>(ns: N) {
        return function t(key: KeyOf<TDict[N]>, vars?: Record<string, string | number>): string {
            const namespace = dictionary[ns];
            if (namespace === undefined) {
                throw new Error(`Unknown string namespace: ${ns}`);
            }
            const raw = namespace[key];
            if (raw === undefined) {
                throw new Error(`Unknown string key: ${ns}.${key}`);
            }
            if (vars === undefined) return raw;
            return Object.entries(vars).reduce(
                (acc, [name, value]) => acc.replaceAll(`{${name}}`, String(value)),
                raw,
            );
        };
    };
}
