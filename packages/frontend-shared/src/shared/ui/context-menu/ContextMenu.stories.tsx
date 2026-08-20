import { ContextMenu } from "./ContextMenu";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning behind this local shape.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof ContextMenu.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Overlays/ContextMenu",
    component: ContextMenu.Root,
};
export default meta;

export const Default: Story = {
    render: () => (
        <ContextMenu.Root>
            <ContextMenu.Trigger className="flex h-24 w-64 items-center justify-center rounded-card border border-border-subtle text-body text-text-secondary">
                Right-click this row
            </ContextMenu.Trigger>
            <ContextMenu.Popup>
                <ContextMenu.Item>Open job</ContextMenu.Item>
                <ContextMenu.Item>Log event</ContextMenu.Item>
                <ContextMenu.Separator />
                <ContextMenu.Item>Archive</ContextMenu.Item>
            </ContextMenu.Popup>
        </ContextMenu.Root>
    ),
};

export const WithShortcutAndDisabledItem: Story = {
    render: () => (
        <ContextMenu.Root>
            <ContextMenu.Trigger className="flex h-24 w-64 items-center justify-center rounded-card border border-border-subtle text-body text-text-secondary">
                Right-click this row
            </ContextMenu.Trigger>
            <ContextMenu.Popup>
                <ContextMenu.Item shortcut="⌘O">Open job</ContextMenu.Item>
                <ContextMenu.Item disabled>Restore (unavailable)</ContextMenu.Item>
                <ContextMenu.Separator />
                <ContextMenu.Item shortcut="⌫">Archive</ContextMenu.Item>
            </ContextMenu.Popup>
        </ContextMenu.Root>
    ),
};
