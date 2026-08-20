import type { ReactNode } from "react";
import { Grid, type SpacingRole } from "./Grid";
import { Text } from "../text";

/** See `Logo.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Grid;
}

interface Story {
    readonly args: {
        readonly columns: number;
        readonly gap: SpacingRole;
        readonly children: ReactNode;
    };
}

const meta: StoryMeta = {
    title: "Shared/UI/Grid",
    component: Grid,
};

export default meta;

const cells = Array.from({ length: 6 }, (_, index) => (
    <Text key={index} variant="body">Cell {index + 1}</Text>
));

export const StackGap: Story = {
    args: { columns: 3, gap: "stack", children: cells },
};

export const SectionGap: Story = {
    args: { columns: 3, gap: "section-gap", children: cells },
};
