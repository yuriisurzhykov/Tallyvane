import type { ReactNode } from "react";
import { Surface } from "./Surface";
import { Text } from "../text";

/**
 * `@storybook/react-vite`'s `Meta`/`StoryObj` types are not resolvable from
 * within `frontend-shared` — see `Logo.stories.tsx` for the same note. This
 * local shape mirrors CSF3's runtime contract closely enough for Storybook
 * to read it correctly.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Surface;
}

interface Story {
    readonly args: {
        readonly variant?: "primary" | "elevated" | "inset";
        readonly children: ReactNode;
    };
}

const meta: StoryMeta = {
    title: "Shared/UI/Surface",
    component: Surface,
};

export default meta;

// `Surface` renders `children` as-is ("caller decides" typography, same as `Panel`) — `Text` here demonstrates the intended real usage.
export const Primary: Story = {
    args: { variant: "primary", children: <Text variant="body">Surface — primary</Text> },
};

export const Elevated: Story = {
    args: { variant: "elevated", children: <Text variant="body">Surface — elevated</Text> },
};

export const Inset: Story = {
    args: { variant: "inset", children: <Text variant="body">Surface — inset</Text> },
};
