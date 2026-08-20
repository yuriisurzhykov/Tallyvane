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
}

const meta: StoryMeta<VisuallyHiddenProps> = {
    title: "Typography and marks/VisuallyHidden",
    component: VisuallyHidden,
};
export default meta;

export const Default: Story<VisuallyHiddenProps> = {
    args: { children: "Only announced to screen readers" },
};

export const AsLabel: Story<VisuallyHiddenProps> = {
    args: { render: <label />, children: "Accessible label for an icon-only control" },
};
