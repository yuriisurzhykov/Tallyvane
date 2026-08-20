import { Button } from "../button";
import { Drawer } from "./Drawer";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning behind this local shape.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Drawer.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Overlays/Drawer",
    component: Drawer.Root,
};
export default meta;

export const Default: Story = {
    render: () => (
        <Drawer.Root>
            <Drawer.Trigger render={<Button tone="primary">Add job</Button>} />
            <Drawer.Popup>
                <Drawer.Title>Add job</Drawer.Title>
                <Drawer.Description>Paste a posting URL or enter the details by hand.</Drawer.Description>
                <p>Form fields go here.</p>
            </Drawer.Popup>
        </Drawer.Root>
    ),
};

export const WithClose: Story = {
    render: () => (
        <Drawer.Root>
            <Drawer.Trigger render={<Button tone="primary">Log interview</Button>} />
            <Drawer.Popup>
                <div className="flex items-start justify-between gap-stack">
                    <Drawer.Title>Log interview</Drawer.Title>
                    <Drawer.Close label="Close drawer" />
                </div>
                <Drawer.Description>Record the round, participants and questions asked.</Drawer.Description>
                <p>Form fields go here.</p>
            </Drawer.Popup>
        </Drawer.Root>
    ),
};

export const OpenByDefault: Story = {
    render: () => (
        <Drawer.Root defaultOpen>
            <Drawer.Trigger render={<Button tone="primary">Schedule interview</Button>} />
            <Drawer.Popup>
                <div className="flex items-start justify-between gap-stack">
                    <Drawer.Title>Schedule interview</Drawer.Title>
                    <Drawer.Close label="Close drawer" />
                </div>
                <p>Form fields go here.</p>
            </Drawer.Popup>
        </Drawer.Root>
    ),
};
