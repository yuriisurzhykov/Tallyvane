import { IconButton, type IconButtonProps } from "./IconButton";

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

/** A placeholder glyph — real icons arrive once `Icon`'s own API is decided (see `COMPONENTS.md` §13). */
function PlaceholderIcon() {
    return (
        <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M2 8h12M8 2v12" stroke="currentColor" strokeWidth="1.5" fill="none" />
        </svg>
    );
}

const meta: StoryMeta<IconButtonProps> = {
    title: "Actions/IconButton",
    component: IconButton,
};
export default meta;

export const Primary: Story<IconButtonProps> = { args: { tone: "primary", label: "Add", children: <PlaceholderIcon /> } };
export const Neutral: Story<IconButtonProps> = { args: { tone: "neutral", label: "Edit", children: <PlaceholderIcon /> } };
export const Ghost: Story<IconButtonProps> = { args: { tone: "ghost", label: "More actions", children: <PlaceholderIcon /> } };
export const Danger: Story<IconButtonProps> = { args: { tone: "danger", label: "Delete", children: <PlaceholderIcon /> } };

export const Small: Story<IconButtonProps> = { args: { tone: "neutral", size: "sm", label: "Edit", children: <PlaceholderIcon /> } };
export const Medium: Story<IconButtonProps> = { args: { tone: "neutral", size: "md", label: "Edit", children: <PlaceholderIcon /> } };
export const Large: Story<IconButtonProps> = { args: { tone: "neutral", size: "lg", label: "Edit", children: <PlaceholderIcon /> } };

export const AsLink: Story<IconButtonProps> = {
    args: { tone: "ghost", label: "View job posting", render: <a href="/jobs/123" />, children: <PlaceholderIcon /> },
};
