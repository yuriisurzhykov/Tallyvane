import { Avatar } from "./Avatar";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Menu.stories.tsx` for the
 * full reasoning behind this local shape and its `render`-form stories:
 * `Avatar` is a compound object (`Root`/`Image`/`Fallback`), not a single
 * `component` function `args` can be handed to.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Avatar.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Marks and identity/Avatar",
    component: Avatar.Root,
};
export default meta;

export const InitialsFallback: Story = {
    render: () => (
        <Avatar.Root>
            <Avatar.Fallback>AL</Avatar.Fallback>
        </Avatar.Root>
    ),
};

/** A `src` that never resolves — the same "no image" fallback shape, reached the other way. */
export const BrokenImageFallback: Story = {
    render: () => (
        <Avatar.Root>
            <Avatar.Image src="https://example.invalid/broken.png" alt="Ada Lovelace"/>
            <Avatar.Fallback>AL</Avatar.Fallback>
        </Avatar.Root>
    ),
};

export const Small: Story = {
    render: () => (
        <Avatar.Root size="sm">
            <Avatar.Fallback>AL</Avatar.Fallback>
        </Avatar.Root>
    ),
};

export const Medium: Story = {
    render: () => (
        <Avatar.Root size="md">
            <Avatar.Fallback>AL</Avatar.Fallback>
        </Avatar.Root>
    ),
};

export const Large: Story = {
    render: () => (
        <Avatar.Root size="lg">
            <Avatar.Fallback>AL</Avatar.Fallback>
        </Avatar.Root>
    ),
};
