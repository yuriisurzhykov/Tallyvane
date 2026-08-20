import { SkipLink } from "./SkipLink";

/** See `button/Button.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof SkipLink;
}

interface Story {
    readonly args: {
        readonly href: string;
        readonly children: string;
    };
}

const meta: StoryMeta = {
    title: "Actions/SkipLink",
    component: SkipLink,
};
export default meta;

// Tab into the canvas to see it appear — it is `sr-only` at rest by design.
export const Default: Story = {
    args: { href: "#main-content", children: "Skip to main content" },
};
