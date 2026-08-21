import { Dot, type DotProps } from "./Dot";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — importing them here would be a
 * cross-package type import this package cannot resolve, and adding the
 * dependency here is out of scope for this batch. This local shape covers
 * only what a CSF3 story file actually needs: a `{ title, component }`
 * default export and `{ args }` named exports.
 */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
    /** No visible text — a bare dot has no `label`. Opts out of the APCA suite's text-contrast check, which has nothing to measure here. */
    readonly tags?: readonly string[];
}

const meta: StoryMeta<DotProps> = {
    title: "Typography and marks/Dot",
    component: Dot,
};
export default meta;

export const Neutral: Story<DotProps> = { args: { tone: "neutral" }, tags: ["no-visible-text"] };
export const Info: Story<DotProps> = { args: { tone: "info" }, tags: ["no-visible-text"] };
export const Attention: Story<DotProps> = { args: { tone: "attention" }, tags: ["no-visible-text"] };
export const Success: Story<DotProps> = { args: { tone: "success" }, tags: ["no-visible-text"] };
export const Danger: Story<DotProps> = { args: { tone: "danger" }, tags: ["no-visible-text"] };

// `label` is wired through `VisuallyHidden` (`Dot.tsx`'s own comment) — never visible, screen-reader-only, so this story has no visible text to check either.
export const WithLabel: Story<DotProps> = { args: { tone: "danger", label: "Overdue" }, tags: ["no-visible-text"] };
