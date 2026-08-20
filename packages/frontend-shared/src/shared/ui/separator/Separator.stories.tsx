import { Separator } from "./Separator";

/** See `Logo.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Separator;
}

interface Story {
    readonly args: {
        readonly orientation?: "horizontal" | "vertical";
        readonly decorative?: boolean;
    };
}

const meta: StoryMeta = {
    title: "Shared/UI/Separator",
    component: Separator,
};

export default meta;

export const Horizontal: Story = {
    args: { orientation: "horizontal" },
};

export const Vertical: Story = {
    args: { orientation: "vertical" },
};

export const Decorative: Story = {
    args: { orientation: "horizontal", decorative: true },
};
