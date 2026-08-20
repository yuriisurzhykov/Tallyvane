import { PasswordField, type PasswordFieldProps } from "./PasswordField";

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

const TOGGLE_LABELS = { showPasswordLabel: "Show password", hidePasswordLabel: "Hide password" };

const meta: StoryMeta<PasswordFieldProps> = {
    title: "Inputs/PasswordField",
    component: PasswordField,
};
export default meta;

export const Default: Story<PasswordFieldProps> = {
    args: { "aria-label": "Password", ...TOGGLE_LABELS },
};

export const Invalid: Story<PasswordFieldProps> = {
    args: { "aria-label": "Password", "aria-invalid": "true", defaultValue: "short", ...TOGGLE_LABELS },
};

export const Disabled: Story<PasswordFieldProps> = {
    args: { "aria-label": "Password", disabled: true, defaultValue: "Cannot be edited", ...TOGGLE_LABELS },
};
