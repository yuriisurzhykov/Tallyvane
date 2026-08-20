import { KeyboardKey, type KeyboardKeyProps } from "./KeyboardKey";

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

const meta: StoryMeta<KeyboardKeyProps> = {
    title: "Typography and marks/KeyboardKey",
    component: KeyboardKey,
};
export default meta;

export const SingleKey: Story<KeyboardKeyProps> = { args: { children: "Esc" } };

/**
 * A combination is composed by the caller from two instances plus a
 * separator — `KeyboardKey` deliberately has no `keys` array prop.
 */
export const Combination: Story<KeyboardKeyProps> = {
    args: {
        children: (
            <>
                <KeyboardKey>Ctrl</KeyboardKey>+<KeyboardKey>K</KeyboardKey>
            </>
        ),
    },
};
