import { IconButton } from "../icon-button";
import { Tooltip } from "./Tooltip";

/** A placeholder glyph — real icons arrive once `Icon`'s own API is decided (see `COMPONENTS.md` §13). */
function PlaceholderIcon() {
    return (
        <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M2 8h12M8 2v12" stroke="currentColor" strokeWidth="1.5" fill="none"/>
        </svg>
    );
}

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning behind this local shape.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Tooltip.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Overlays/Tooltip",
    component: Tooltip.Root,
};
export default meta;

export const Default: Story = {
    render: () => (
        <Tooltip.Root>
            <Tooltip.Trigger render={ <IconButton label="Archive job" tone="ghost"><PlaceholderIcon/></IconButton> }/>
            <Tooltip.Popup>Archive job</Tooltip.Popup>
        </Tooltip.Root>
    ),
};

export const WithArrow: Story = {
    render: () => (
        <Tooltip.Root>
            <Tooltip.Trigger render={ <IconButton label="Pin to Today" tone="ghost"><PlaceholderIcon/></IconButton> }/>
            <Tooltip.Popup side="bottom">
                <Tooltip.Arrow/>
                Pin to Today
            </Tooltip.Popup>
        </Tooltip.Root>
    ),
};

/** Wrapping several triggers in one `Provider` means only the first hover in the row pays the full open delay. */
export const SharedDelayAcrossARow: Story = {
    render: () => (
        <Tooltip.Provider>
            <div className="flex gap-inline-tight">
                <Tooltip.Root>
                    <Tooltip.Trigger render={ <IconButton label="Edit" tone="ghost"><PlaceholderIcon/></IconButton> }/>
                    <Tooltip.Popup>Edit</Tooltip.Popup>
                </Tooltip.Root>
                <Tooltip.Root>
                    <Tooltip.Trigger
                        render={ <IconButton label="Archive" tone="ghost"><PlaceholderIcon/></IconButton> }/>
                    <Tooltip.Popup>Archive</Tooltip.Popup>
                </Tooltip.Root>
            </div>
        </Tooltip.Provider>
    ),
};
