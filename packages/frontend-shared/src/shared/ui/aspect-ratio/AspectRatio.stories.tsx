import type { ReactNode } from "react";
import { AspectRatio } from "./AspectRatio";

/** See `Logo.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof AspectRatio;
}

interface Story {
    readonly args: {
        readonly ratio: number;
        readonly children: ReactNode;
    };
    /** No visible text in this story — a plain colour swatch fills the box. Opts out of the APCA suite's text-contrast check, which has nothing to measure here. */
    readonly tags?: readonly string[];
}

const meta: StoryMeta = {
    title: "Shared/UI/AspectRatio",
    component: AspectRatio,
};

export default meta;

export const Widescreen: Story = {
    args: {
        ratio: 16 / 9,
        children: <div className="h-full w-full bg-surface-inset" />,
    },
    tags: ["no-visible-text"],
};

export const Square: Story = {
    args: {
        ratio: 1,
        children: <div className="h-full w-full bg-surface-inset" />,
    },
    tags: ["no-visible-text"],
};
