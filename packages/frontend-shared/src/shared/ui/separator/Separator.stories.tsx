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
    /** No visible text — a separator is a bare line. Opts out of the APCA suite's text-contrast check, which has nothing to measure here. */
    readonly tags?: readonly string[];
}

const meta: StoryMeta = {
    title: "Shared/UI/Separator",
    component: Separator,
};

export default meta;

export const Horizontal: Story = {
    args: { orientation: "horizontal" },
    tags: ["no-visible-text"],
};

export const Vertical: Story = {
    args: { orientation: "vertical" },
    tags: ["no-visible-text"],
};

export const Decorative: Story = {
    args: { orientation: "horizontal", decorative: true },
    tags: ["no-visible-text"],
};
