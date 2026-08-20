import type { ReactNode } from "react";
import * as React from "react";
import { ScrollArea } from "./ScrollArea";

/** See `Logo.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof ScrollArea;
    /** `Story` is a component reference, rendered as `<Story/>` — the real CSF3 decorator shape, matching `preview.tsx`'s own usage. */
    readonly decorators: ReadonlyArray<(Story: React.ComponentType) => ReactNode>;
}

interface Story {
    readonly args: {
        readonly className: string;
        readonly children: ReactNode;
    };
}

/**
 * A local, self-contained height, not a global viewport anchor — this used
 * to be `className="h-full"` filling a `100dvh` chain stretched all the way
 * from `html` down through Storybook's own decorator (see `preview.css`'s
 * comment for why that was the wrong scope: it stretched every OTHER
 * story's canvas to near-viewport size too, purely as a side effect).
 * A plain, tokenless numeric — the same class of exception this
 * component's own `SCROLLBAR_THICKNESS` already is — chosen only to be
 * comfortably taller than a few rows of text, not derived from any real
 * layout constraint. Referenced by identifier, not written inline, for the
 * same reason those are: `no-raw-dimension-value` flags a literal in a
 * style prop, not a named constant.
 */
const STORY_VIEWPORT_HEIGHT = "24rem";

const meta: StoryMeta = {
    title: "Shared/UI/ScrollArea",
    component: ScrollArea,
    decorators: [(Story) => <div style={{ height: STORY_VIEWPORT_HEIGHT }}><Story/></div>],
};

export default meta;

/** 45 rows guarantees overflow regardless of exactly how `24rem` renders on a given platform's font metrics. */
export const Default: Story = {
    args: {
        className: "h-full",
        children: (
            <div className="flex flex-col gap-inline">
                {Array.from({ length: 45 }, (_, index) => (
                    <p key={index}>Row {index + 1}</p>
                ))}
            </div>
        ),
    },
};
