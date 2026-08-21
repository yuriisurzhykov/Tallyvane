import { VisuallyHidden, type VisuallyHiddenProps } from "./VisuallyHidden";

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
    /**
     * No visible text, deliberately — the entire point of this component is
     * that nothing here is ever painted. Contrast is a question about a
     * rendered pixel's colour; content nothing ever paints has no such
     * question to answer, so this opts out of the APCA suite's
     * text-contrast check rather than needing a `Text` style nobody would
     * ever see anyway.
     */
    readonly tags?: readonly string[];
}

const meta: StoryMeta<VisuallyHiddenProps> = {
    title: "Typography and marks/VisuallyHidden",
    component: VisuallyHidden,
};
export default meta;

export const Default: Story<VisuallyHiddenProps> = {
    args: { children: "Only announced to screen readers" },
    tags: ["no-visible-text"],
};

export const AsLabel: Story<VisuallyHiddenProps> = {
    args: { render: <label />, children: "Accessible label for an icon-only control" },
    tags: ["no-visible-text"],
};
