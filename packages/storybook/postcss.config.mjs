/**
 * Identical to frontend-web's own postcss.config.mjs. Vite (the builder
 * behind @storybook/react-vite) auto-discovers this file the same way Next's
 * own bundler does, so Tailwind is processed by the exact same plugin the
 * real app uses rather than a second, separately configured pipeline.
 */
export default {
    plugins: {
        "@tailwindcss/postcss": {},
    },
};
