import { Field, type FieldProps } from "./Field";
import { Input } from "../input";

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

const meta: StoryMeta<FieldProps> = {
    title: "Inputs/Field",
    component: Field,
};
export default meta;

export const Default: Story<FieldProps> = {
    args: {
        label: "Email",
        children: <Input type="email" />,
    },
};

export const WithDescription: Story<FieldProps> = {
    args: {
        label: "Email",
        description: "We only use this to send interview invites.",
        children: <Input type="email" />,
    },
};

export const WithError: Story<FieldProps> = {
    args: {
        label: "Email",
        error: "Enter a valid email address.",
        children: <Input type="email" />,
    },
};

export const Required: Story<FieldProps> = {
    args: {
        label: "Email",
        required: true,
        children: <Input type="email" />,
    },
};
