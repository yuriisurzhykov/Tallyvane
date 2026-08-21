import { Collapsible } from "./Collapsible";
import { Text } from "../text";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Menu.stories.tsx` for the
 * full reasoning behind this local shape and its `render`-form stories:
 * `Collapsible` is a compound object (`Root`/`Trigger`/`Panel`), not a
 * single `component` function `args` can be handed to.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Collapsible.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Disclosure/Collapsible",
    component: Collapsible.Root,
};
export default meta;

// `Collapsible.Panel` deliberately forces no typography of its own (see its own comment) — the demo text is wrapped in `Text` here, matching what a real caller would render inside it.
export const Closed: Story = {
    render: () => (
        <Collapsible.Root>
            <Collapsible.Trigger>Show details</Collapsible.Trigger>
            <Collapsible.Panel className="p-stack"><Text variant="body">The hidden details, revealed on open.</Text></Collapsible.Panel>
        </Collapsible.Root>
    ),
};

export const Open: Story = {
    render: () => (
        <Collapsible.Root defaultOpen>
            <Collapsible.Trigger>Show details</Collapsible.Trigger>
            <Collapsible.Panel className="p-stack"><Text variant="body">The hidden details, revealed on open.</Text></Collapsible.Panel>
        </Collapsible.Root>
    ),
};

export const Disabled: Story = {
    render: () => (
        <Collapsible.Root disabled>
            <Collapsible.Trigger>Show details</Collapsible.Trigger>
            <Collapsible.Panel className="p-stack"><Text variant="body">The hidden details, revealed on open.</Text></Collapsible.Panel>
        </Collapsible.Root>
    ),
};
