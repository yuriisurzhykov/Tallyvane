import { Surface } from "./Surface";

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
        readonly children: string;
    };
}

const meta: StoryMeta = {
    title: "Shared/UI/Surface",
    component: Surface,
};

export default meta;

export const Primary: Story = {
    args: { variant: "primary", children: "Surface — primary" },
};

export const Elevated: Story = {
    args: { variant: "elevated", children: "Surface — elevated" },
};

export const Inset: Story = {
    args: { variant: "inset", children: "Surface — inset" },
};
