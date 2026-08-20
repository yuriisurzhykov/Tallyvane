import { Input, type InputProps } from "./Input";

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
     * No visible text — `placeholder` is an attribute, not rendered DOM text,
     * and a native `<input>`'s `defaultValue` likewise never appears as a
     * child text node the way a `<textarea>`'s does. Opts out of the APCA
     * suite's text-contrast check, which has nothing to measure here.
     */
    readonly tags?: readonly string[];
}

const meta: StoryMeta<InputProps> = {
    title: "Inputs/Input",
    component: Input,
};
export default meta;

export const Small: Story<InputProps> = {
    args: { size: "sm", placeholder: "e.g. Colm Tuite", "aria-label": "Name" },
    tags: ["no-visible-text"],
};

export const Medium: Story<InputProps> = {
    args: { size: "md", placeholder: "e.g. Colm Tuite", "aria-label": "Name" },
    tags: ["no-visible-text"],
};

export const Large: Story<InputProps> = {
    args: { size: "lg", placeholder: "e.g. Colm Tuite", "aria-label": "Name" },
    tags: ["no-visible-text"],
};

export const Invalid: Story<InputProps> = {
    args: { "aria-invalid": "true", defaultValue: "not-an-email", "aria-label": "Email" },
    tags: ["no-visible-text"],
};

export const Disabled: Story<InputProps> = {
    args: { disabled: true, defaultValue: "Cannot be edited", "aria-label": "Name" },
    tags: ["no-visible-text"],
};
