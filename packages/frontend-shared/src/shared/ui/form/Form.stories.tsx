import { Form, type FormProps } from "./Form";
import { Field } from "../field";
import { Input } from "../input";
import { Button } from "../button";

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

const meta: StoryMeta<FormProps> = {
    title: "Inputs/Form",
    component: Form,
};
export default meta;

export const Default: Story<FormProps> = {
    args: {
        children: (
            <>
                <Field label="Email">
                    <Input name="email" type="email" />
                </Field>
                <Button tone="primary" type="submit">
                    Submit
                </Button>
            </>
        ),
    },
};

/**
 * The realistic call-site shape for server-side errors: `errors` is passed
 * to `Form` so its own submit-blocking/first-invalid-focus logic knows about
 * it, and the SAME message is threaded into `Field`'s own `error` prop by
 * hand — `Field` does not read `Form`'s error context automatically, see
 * `Form.tsx`'s doc comment for why.
 */
export const WithServerError: Story<FormProps> = {
    args: {
        errors: { email: "This email is already registered" },
        children: (
            <>
                <Field label="Email" error="This email is already registered">
                    <Input name="email" type="email" />
                </Field>
                <Button tone="primary" type="submit">
                    Submit
                </Button>
            </>
        ),
    },
};
