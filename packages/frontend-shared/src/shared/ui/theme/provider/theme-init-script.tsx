import { THEME_CLASS, THEME_STORAGE_KEY } from "./constants";

const SCRIPT_ESCAPES: Record<string, string> = {
    "<": "\\u003C",
    ">": "\\u003E",
    "/": "\\u002F",
    "\\": "\\\\",
    "\u2028": "\\u2028",
    "\u2029": "\\u2029",
};

/** Everything interpolated into the script body goes through this. A value containing `</script` would otherwise end the tag early and turn the rest into markup. */
function escapeForScript(value: string): string {
    return value.replace(/[<>/\\\u2028\u2029]/g, (character) => SCRIPT_ESCAPES[character] ?? character);
}

/**
 * A synchronous, blocking `<script>` that runs while the HTML is being parsed —
 * before hydration starts and before the first paint — and applies the stored
 * theme directly to the root element.
 *
 * This is the only thing that actually closes the flash-of-wrong-theme window.
 * The provider's effects run after hydration AND after the first paint, so a
 * reader whose stored preference differs from the default would see the default
 * first, every time. Waiting longer in a test does not fix it either: nothing a
 * test can wait for corresponds to a React effect.
 *
 * A cookie read on the server was considered and rejected: it would opt the
 * route out of static rendering, and cookie-varying HTML in a shared cache is
 * how one reader's preference ends up served to everyone.
 *
 * It mirrors the provider's own resolve-and-apply logic by hand, because it has
 * to run standalone before any application JavaScript — including this
 * project's own bundle. If that logic changes, this needs the same change,
 * which is why both are short.
 *
 * The root element needs `suppressHydrationWarning` alongside this: the script
 * mutates the DOM before React ever compares it against what the server sent.
 */
export function ThemeInitScript() {
    const key = escapeForScript(JSON.stringify(THEME_STORAGE_KEY));
    const dark = escapeForScript(JSON.stringify(THEME_CLASS.dark));
    const light = escapeForScript(JSON.stringify(THEME_CLASS.light));

    const script =
        `(function(){try{` +
        `var s=localStorage.getItem(${key});` +
        `var t=(s==="light"||s==="dark")?s:(window.matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light");` +
        `var c=document.documentElement.classList;` +
        `c.remove(${dark},${light});c.add(t==="dark"?${dark}:${light});` +
        `}catch(e){}})()`;

    // Inline and synchronous on purpose: it must block parsing and run before
    // hydration, or it cannot prevent the flash it exists to prevent.
    return <script dangerouslySetInnerHTML={{ __html: script }} />;
}
