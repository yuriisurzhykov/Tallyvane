import type { ReactNode } from "react";
import { Row, type SpacingRole } from "./Row";

/** See `Logo.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Row;
}

interface Story {
    readonly args: {
        readonly gap: SpacingRole;
        readonly children: ReactNode;
    };
}

const meta: StoryMeta = {
    title: "Shared/UI/Row",
    component: Row,
};

export default meta;

const items = (
    <>
        <span>Icon</span>
        <span>Label</span>
    </>
);

export const InlineTightGap: Story = {
    args: { gap: "inline-tight", children: items },
};

export const GroupGap: Story = {
    args: { gap: "group-gap", children: items },
};
