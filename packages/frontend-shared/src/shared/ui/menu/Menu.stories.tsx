import { Button } from "../button";
import { Menu } from "./Menu";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning behind this local shape.
 *
 * Unlike `Button.stories.tsx`'s sibling files, `Menu` has no single
 * `component` function to hand `args` to — it is a compound object
 * (`Root`/`Trigger`/`Popup`/`Item`/`Separator`) assembled differently per
 * story, the same shape `Popover.stories.tsx` already uses for the other
 * compound overlay in this package. Every story below therefore uses
 * CSF3's `render` form instead of `args`.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Menu.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

/** A placeholder glyph — real icons arrive once `Icon`'s own API is decided (see `COMPONENTS.md` §13), same stand-in `IconButton.stories.tsx` already uses. */
function PlaceholderIcon() {
    return (
        <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M2 8h12M8 2v12" stroke="currentColor" strokeWidth="1.5" fill="none" />
        </svg>
    );
}

const meta: StoryMeta = {
    title: "Actions/Menu",
    component: Menu.Root,
};
export default meta;

export const Default: Story = {
    render: () => (
        <Menu.Root>
            <Menu.Trigger render={<Button tone="neutral">Actions</Button>} />
            <Menu.Popup>
                <Menu.Item>Rename</Menu.Item>
                <Menu.Item>Duplicate</Menu.Item>
                <Menu.Item>Delete</Menu.Item>
            </Menu.Popup>
        </Menu.Root>
    ),
};

export const WithIcon: Story = {
    render: () => (
        <Menu.Root>
            <Menu.Trigger render={<Button tone="neutral">Actions</Button>} />
            <Menu.Popup>
                <Menu.Item leadingIcon={<PlaceholderIcon />}>Rename</Menu.Item>
                <Menu.Item leadingIcon={<PlaceholderIcon />}>Duplicate</Menu.Item>
            </Menu.Popup>
        </Menu.Root>
    ),
};

export const WithShortcut: Story = {
    render: () => (
        <Menu.Root>
            <Menu.Trigger render={<Button tone="neutral">Actions</Button>} />
            <Menu.Popup>
                <Menu.Item shortcut="⌘C">Copy</Menu.Item>
                <Menu.Item shortcut="⌘V">Paste</Menu.Item>
            </Menu.Popup>
        </Menu.Root>
    ),
};

export const WithSeparator: Story = {
    render: () => (
        <Menu.Root>
            <Menu.Trigger render={<Button tone="neutral">Actions</Button>} />
            <Menu.Popup>
                <Menu.Item>Rename</Menu.Item>
                <Menu.Item>Duplicate</Menu.Item>
                <Menu.Separator />
                <Menu.Item>Delete</Menu.Item>
            </Menu.Popup>
        </Menu.Root>
    ),
};

export const WithDisabledItem: Story = {
    render: () => (
        <Menu.Root>
            <Menu.Trigger render={<Button tone="neutral">Actions</Button>} />
            <Menu.Popup>
                <Menu.Item>Rename</Menu.Item>
                <Menu.Item disabled>Duplicate (unavailable)</Menu.Item>
                <Menu.Item>Delete</Menu.Item>
            </Menu.Popup>
        </Menu.Root>
    ),
};
