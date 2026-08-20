import type { ReactNode } from "react";
import { Stack, type SpacingRole } from "./Stack";

/** See `Logo.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Stack;
}

interface Story {
    readonly args: {
        readonly gap: SpacingRole;
        readonly children: ReactNode;
    };
}

const meta: StoryMeta = {
    title: "Shared/UI/Stack",
    component: Stack,
};

export default meta;

const items = (
    <>
        <p>First</p>
        <p>Second</p>
        <p>Third</p>
    </>
);

export const StackTightGap: Story = {
    args: { gap: "stack-tight", children: items },
};

export const SectionGap: Story = {
    args: { gap: "section-gap", children: items },
};
