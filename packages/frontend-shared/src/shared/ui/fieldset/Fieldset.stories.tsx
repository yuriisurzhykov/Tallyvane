import { Fieldset, type FieldsetProps } from "./Fieldset";
import { Text } from "../text";

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

const meta: StoryMeta<FieldsetProps> = {
    title: "Inputs/Fieldset",
    component: Fieldset,
};
export default meta;

export const Default: Story<FieldsetProps> = {
    args: {
        legend: "Notification preferences",
        children: (
            <>
                <Text variant="body" render={<label />}>
                    <input type="checkbox" /> Email alerts
                </Text>
                <Text variant="body" render={<label />}>
                    <input type="checkbox" /> SMS alerts
                </Text>
            </>
        ),
    },
};
