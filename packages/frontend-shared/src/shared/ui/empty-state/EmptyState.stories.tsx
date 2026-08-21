import { EmptyState, type EmptyStateProps } from "./EmptyState";
import { Button } from "../button";

/** See `panel/Panel.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

/** A placeholder glyph — real icons arrive once `Icon`'s own API is decided (see `COMPONENTS.md` §13). */
function PlaceholderIcon() {
    return (
        <svg width="32" height="32" viewBox="0 0 16 16" aria-hidden="true">
            <path
                d="M2 4h12v9a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4Zm0 0 2-2h8l2 2"
                stroke="currentColor"
                strokeWidth="1.5"
                fill="none"
                strokeLinejoin="round"
            />
        </svg>
    );
}

const meta: StoryMeta<EmptyStateProps> = {
    title: "Compounds/EmptyState",
    component: EmptyState,
};
export default meta;

export const TitleOnly: Story<EmptyStateProps> = {
    args: { title: "No applications yet" },
};

export const WithDescription: Story<EmptyStateProps> = {
    args: {
        title: "No applications yet",
        description: "Applications you log will show up here as soon as you capture your first job.",
    },
};

export const WithIcon: Story<EmptyStateProps> = {
    args: {
        icon: <PlaceholderIcon />,
        title: "No applications yet",
        description: "Applications you log will show up here as soon as you capture your first job.",
    },
};

export const WithAction: Story<EmptyStateProps> = {
    args: {
        icon: <PlaceholderIcon />,
        title: "No applications yet",
        description: "Applications you log will show up here as soon as you capture your first job.",
        action: <Button tone="primary">Add your first application</Button>,
    },
};
