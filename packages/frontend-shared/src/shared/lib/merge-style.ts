import type { CSSProperties } from "react";

/** Base UI's own shape for `style` (and `className`): a plain value, or a function of the component's state — verified against `@base-ui/react/internals`'s `BaseUIComponentProps`. */
export type BaseUIStyle<State> = CSSProperties | ((state: State) => CSSProperties | undefined) | undefined;

/**
 * Layers a component's own fixed styles on top of whatever `style` a caller
 * passed through — without assuming that `style` is a plain object.
 *
 * `{ ...style, ...extra }` is the obvious-looking version, and it is a real
 * bug the moment a caller passes a Base UI state-callback for `style`
 * (`(state) => ({...})`, the form every `BaseUIComponentProps` accepts):
 * spreading a function copies none of its own enumerable properties, so the
 * caller's style silently vanishes and only `extra` survives. Returning a
 * function unconditionally — resolving the caller's value first, static or
 * not — is what Base UI's own `style` prop already accepts natively, so
 * nothing downstream needs to know which case a caller supplied.
 */
export function mergeStyle<State>(style: BaseUIStyle<State>, extra: CSSProperties): (state: State) => CSSProperties {
    return (state) => ({
        ...(typeof style === "function" ? style(state) : style),
        ...extra,
    });
}
