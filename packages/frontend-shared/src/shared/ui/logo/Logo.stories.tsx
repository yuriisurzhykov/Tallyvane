import { Logo } from "./Logo";

/**
 * `@storybook/react-vite`'s `Meta`/`StoryObj` types are not resolvable from
 * within `frontend-shared` — the dependency is only declared in
 * `packages/storybook`, and this package must not add it just to type a
 * story file. This local shape mirrors CSF3's actual runtime contract
 * closely enough for Storybook to read it correctly.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Logo;
}

interface Story {
    readonly args: {
        readonly text: string;
    };
}

const meta: StoryMeta = {
    title: "Shared/UI/Logo",
    component: Logo,
};

export default meta;

// "Acme Corp", never the real product name — this story doubles as a visual
// witness that the wordmark truly comes from `text`, not a hardcoded value.
export const Default: Story = {
    args: {
        text: "Acme Corp",
    },
};
