/**
 * Tailwind v4 runs as a PostCSS plugin and needs no configuration file of its
 * own — the theme lives in CSS, in the `frontend-shared` package's
 * `ui/theme/adapters/tailwind.css`.
 */
export default {
    plugins: {
        "@tailwindcss/postcss": {},
    },
};
