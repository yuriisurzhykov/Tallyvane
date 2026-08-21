import { Tabs } from "./Tabs";
import { Text } from "../text";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Menu.stories.tsx` for the
 * full reasoning behind this local shape and its `render`-form stories:
 * `Tabs` is a compound object (`Root`/`List`/`Tab`/`Indicator`/`Panel`),
 * not a single `component` function `args` can be handed to.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Tabs.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Disclosure/Tabs",
    component: Tabs.Root,
};
export default meta;

// `Tabs.Panel` deliberately forces no typography of its own (`Tabs.tsx`'s own comment) — each panel's demo text is wrapped in `Text` here, matching what a real call site would render inside it.
export const Horizontal: Story = {
    render: () => (
        <Tabs.Root>
            <Tabs.List>
                <Tabs.Tab value="pipeline">Pipeline</Tabs.Tab>
                <Tabs.Tab value="board">Board</Tabs.Tab>
                <Tabs.Tab value="analytics" disabled>
                    Analytics
                </Tabs.Tab>
                <Tabs.Indicator/>
            </Tabs.List>
            <Tabs.Panel value="pipeline"><Text variant="body">The pipeline table.</Text></Tabs.Panel>
            <Tabs.Panel value="board"><Text variant="body">The pipeline board.</Text></Tabs.Panel>
            <Tabs.Panel value="analytics"><Text variant="body">Analytics (unavailable on this plan).</Text></Tabs.Panel>
        </Tabs.Root>
    ),
};

export const Vertical: Story = {
    render: () => (
        <Tabs.Root orientation="vertical">
            <Tabs.List>
                <Tabs.Tab value="pipeline">Pipeline</Tabs.Tab>
                <Tabs.Tab value="board">Board</Tabs.Tab>
                <Tabs.Indicator/>
            </Tabs.List>
            <Tabs.Panel value="pipeline"><Text variant="body">The pipeline table.</Text></Tabs.Panel>
            <Tabs.Panel value="board"><Text variant="body">The pipeline board.</Text></Tabs.Panel>
        </Tabs.Root>
    ),
};

/** No `Tabs.Indicator` at all — the fallback for a caller that does not want the sliding pill treatment (e.g. a plain underline via each `Tab`'s own `className`). */
export const WithoutIndicator: Story = {
    render: () => (
        <Tabs.Root>
            <Tabs.List>
                <Tabs.Tab value="pipeline">Pipeline</Tabs.Tab>
                <Tabs.Tab value="board">Board</Tabs.Tab>
            </Tabs.List>
            <Tabs.Panel value="pipeline"><Text variant="body">The pipeline table.</Text></Tabs.Panel>
            <Tabs.Panel value="board"><Text variant="body">The pipeline board.</Text></Tabs.Panel>
        </Tabs.Root>
    ),
};
