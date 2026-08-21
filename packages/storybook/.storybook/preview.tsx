import * as React from "react";
import type { Preview } from "@storybook/react-vite";
import type { ThemeId } from "frontend-shared/ui/theme";
import { ThemeProvider, useTheme } from "frontend-shared/ui/theme";
/**
 * Self-hosted, matching `frontend-web/app/fonts.ts`'s own weight/subset
 * selection exactly (`typography.weight`'s full scale for sans; mono's sole
 * consumer, `textStyles.numeric`, only ever sets regular). Each import names
 * a single `latin-<weight>.css` file — Fontsource also ships an unqualified
 * `<weight>.css` covering every subset (cyrillic, greek, vietnamese, ...) in
 * one file via `unicode-range`, but the `latin-`-prefixed one is the literal
 * match for "latin only", not just an equivalent way to get there.
 */
import "@fontsource/ibm-plex-sans/latin-400.css";
import "@fontsource/ibm-plex-sans/latin-500.css";
import "@fontsource/ibm-plex-sans/latin-600.css";
import "@fontsource/ibm-plex-sans/latin-700.css";
import "@fontsource/ibm-plex-mono/latin-400.css";
import "./preview.css";

/**
 * Drives the real `ThemeProvider`'s `setPreference` from Storybook's own
 * toolbar, rather than reimplementing a simplified class toggle — a component
 * previewed here goes through the same context every consumer's app does.
 *
 * No `ThemeInitScript`: that script exists solely to apply the theme before
 * React hydrates a server-rendered page, avoiding a flash of the wrong theme.
 * A Storybook iframe renders fully on the client from a blank document —
 * there is no server-rendered markup to mismatch, so nothing to flash.
 */
function ThemeSync({ themeFromToolbar, children }: {
    readonly themeFromToolbar: ThemeId;
    readonly children: React.ReactNode;
}) {
    const { preference, setPreference } = useTheme();
    React.useEffect(() => {
        if (preference !== themeFromToolbar) setPreference(themeFromToolbar);
    }, [themeFromToolbar, preference, setPreference]);
    return <>{ children }</>;
}

const preview: Preview = {
    parameters: {
        controls: {
            matchers: {
                color: /(background|color)$/i,
                date: /Date$/i,
            },
        },
    },
    globalTypes: {
        theme: {
            description: "Theme",
            toolbar: {
                icon: "mirror",
                items: [
                    { value: "dark", title: "Dark" },
                    { value: "light", title: "Light" },
                ],
                dynamicTitle: true,
            },
        },
    },
    initialGlobals: {
        theme: "dark",
    },
    decorators: [
        (Story, context) => (
            <ThemeProvider>
                <ThemeSync themeFromToolbar={ context.globals.theme as ThemeId }>
                    {/* Sized to content, not stretched to the viewport — see `preview.css`'s
                        comment for why a global height anchor was the wrong scope for that.
                        A story that genuinely needs a real height (`ScrollArea`) supplies its
                        own local one instead. */}
                    <div className="bg-surface-primary p-screen-padding text-text-primary">
                        <Story/>
                    </div>
                </ThemeSync>
            </ThemeProvider>
        ),
    ],
};

export default preview;
