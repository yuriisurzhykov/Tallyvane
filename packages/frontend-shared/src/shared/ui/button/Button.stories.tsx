import { Button, type ButtonProps } from "./Button";

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

const meta: StoryMeta<ButtonProps> = {
    title: "Actions/Button",
    component: Button,
};
export default meta;

export const Primary: Story<ButtonProps> = { args: { tone: "primary", children: "Save changes" } };
export const Neutral: Story<ButtonProps> = { args: { tone: "neutral", children: "Cancel" } };
export const Ghost: Story<ButtonProps> = { args: { tone: "ghost", children: "Dismiss" } };
export const Danger: Story<ButtonProps> = { args: { tone: "danger", children: "Delete application" } };

export const Small: Story<ButtonProps> = { args: { tone: "primary", size: "sm", children: "Save changes" } };
export const Large: Story<ButtonProps> = { args: { tone: "primary", size: "lg", children: "Save changes" } };

export const Loading: Story<ButtonProps> = { args: { tone: "primary", loading: true, children: "Saving\u2026" } };

export const WithIcons: Story<ButtonProps> = {
    args: {
        tone: "primary",
        leadingIcon: <span aria-hidden="true">+</span>,
        trailingIcon: <span aria-hidden="true">{"\u2192"}</span>,
        children: "Add job",
    },
};

export const AsAnchor: Story<ButtonProps> = {
    args: {
        tone: "primary",
        render: <a href="/jobs" />,
        nativeButton: false,
        children: "View jobs",
    },
};
