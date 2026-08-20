import type { StorybookConfig } from "@storybook/react-vite";

/**
 * `@storybook/react-vite`, not `@storybook/nextjs-vite`: nothing in scope
 * today (Tier 0 primitives) touches `next/navigation`, `next/image` or
 * `next/font` — they are plain React plus Base UI plus Tailwind classes, by
 * design (see `docs/frontend/02-component-testing-architecture.md`). Reaching
 * for the Next-specific adapter now would mean carrying its mocking machinery
 * for APIs nothing here calls. Revisit this choice if a future Tier 3+ story,
 * sourced from `frontend-web/src` or `frontend-admin/src`, genuinely needs it.
 *
 * The story glob reads from `frontend-shared` today. It grows to also glob
 * `frontend-web/src/**` and `frontend-admin/src/**` once those layers have
 * Tier 3+ components with their own `.stories.tsx`, without restructuring
 * anything here — this file is the one place that changes.
 */
const config: StorybookConfig = {
    stories: ["../../frontend-shared/src/shared/ui/**/*.stories.@(ts|tsx)"],
    framework: "@storybook/react-vite",
    addons: [],
};

export default config;
