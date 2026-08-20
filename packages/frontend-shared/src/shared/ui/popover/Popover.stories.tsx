import { Button } from "../button";
import { Popover } from "./Popover";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning behind this local shape.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Popover.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Overlays/Popover",
    component: Popover.Root,
};
export default meta;

export const Default: Story = {
    render: () => (
        <Popover.Root>
            <Popover.Trigger render={ <Button tone="neutral">Filter jobs</Button> }/>
            <Popover.Popup>
                <p>Anchored panel content goes here.</p>
            </Popover.Popup>
        </Popover.Root>
    ),
};

export const WithArrow: Story = {
    render: () => (
        <Popover.Root>
            <Popover.Trigger render={ <Button tone="neutral">More filters</Button> }/>
            <Popover.Popup arrow side="bottom">
                <p>Points back at its trigger.</p>
            </Popover.Popup>
        </Popover.Root>
    ),
};

export const WithClose: Story = {
    render: () => (
        <Popover.Root>
            <Popover.Trigger render={ <Button tone="neutral">Bulk actions</Button> }/>
            <Popover.Popup>
                <div className="flex items-start justify-between gap-stack">
                    <p>Choose an action for the selected rows.</p>
                    <Popover.Close label="Close"/>
                </div>
            </Popover.Popup>
        </Popover.Root>
    ),
};

export const OpenOnHover: Story = {
    render: () => (
        <Popover.Root>
            <Popover.Trigger openOnHover render={ <Button tone="ghost">Company size</Button> }/>
            <Popover.Popup side="right">
                <p>Also opens on hover, not just on click.</p>
            </Popover.Popup>
        </Popover.Root>
    ),
};
