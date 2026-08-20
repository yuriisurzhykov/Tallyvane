import type { ReactNode } from "react";
import { ScrollArea } from "./ScrollArea";

/** See `Logo.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof ScrollArea;
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
};

export default meta;

/**
 * `h-full` (a built-in Tailwind utility, not a custom or borrowed-token
 * value) fills whatever real height Storybook's own canvas provides — see
 * `preview.css`/`preview.tsx` for the anchor that makes that height real
 * rather than auto. 45 rows guarantees overflow regardless of the exact
 * pixel height that resolves to.
 */
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
