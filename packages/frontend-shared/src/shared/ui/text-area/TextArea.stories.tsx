import { TextArea, type TextAreaProps } from "./TextArea";

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

const meta: StoryMeta<TextAreaProps> = {
    title: "Inputs/TextArea",
    component: TextArea,
};
export default meta;

export const Empty: Story<TextAreaProps> = {
    args: { placeholder: "Tell us why you're a good fit…", "aria-label": "Cover letter" },
};

export const GrowsWithContent: Story<TextAreaProps> = {
    args: {
        defaultValue:
            "Auto-grows past its minimum height as this text wraps onto more lines, using the native field-sizing: content property — no measurement or mirroring hack behind it.",
        "aria-label": "Cover letter",
    },
};

export const Invalid: Story<TextAreaProps> = {
    args: { "aria-invalid": "true", defaultValue: "Too short.", "aria-label": "Cover letter" },
};

export const Disabled: Story<TextAreaProps> = {
    args: { disabled: true, defaultValue: "Cannot be edited", "aria-label": "Cover letter" },
};
