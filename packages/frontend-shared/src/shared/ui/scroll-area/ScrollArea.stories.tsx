import type { ReactNode } from "react";
import * as React from "react";
import { ScrollArea } from "./ScrollArea";
import { Text } from "../text";

/** See `Logo.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof ScrollArea;
    /** `Story` is a component reference, rendered as `<Story/>` — the real CSF3 decorator shape, matching `preview.tsx`'s own usage. */
    readonly decorators: readonly ((Story: React.ComponentType) => ReactNode)[];
}

interface Story {
    readonly args: {
        readonly className: string;
        readonly children: ReactNode;
    };
}

const meta: StoryMeta = {
    title: "Shared/UI/ScrollArea",
    component: ScrollArea,
    decorators: [(Story) => <div className="story-scroll-canvas"><Story/></div>],
};

export default meta;

/** 45 rows guarantees overflow regardless of exactly how `24rem` renders on a given platform's font metrics. */
export const Default: Story = {
    args: {
        className: "h-full",
        children: (
            <div className="flex flex-col gap-inline">
                {Array.from({ length: 45 }, (_, index) => (
                    <Text key={index} variant="body">Row {index + 1}</Text>
                ))}
            </div>
        ),
    },
};
