import { Button, type ButtonProps } from "./Button";

/** A placeholder glyph — real icons arrive once `Icon`'s own API is decided (see `COMPONENTS.md` §13), the same stand-in `IconButton.stories.tsx`/`Menu.stories.tsx` already use. */
function PlusGlyph() {
    return (
        <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M8 2v12M2 8h12" stroke="currentColor" strokeWidth="1.5" fill="none" />
        </svg>
    );
}

/** Same reasoning as `PlusGlyph` above. */
function ArrowRightGlyph() {
    return (
        <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M3 8h10M9 4l4 4-4 4" stroke="currentColor" strokeWidth="1.5" fill="none" />
        </svg>
    );
}

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

const meta: StoryMeta<ButtonProps> = {
    title: "Actions/Button",
    component: Button,
};
export default meta;

export const Primary: Story<ButtonProps> = { args: { tone: "primary", children: "Save changes" } };
export const Neutral: Story<ButtonProps> = { args: { tone: "neutral", children: "Cancel" } };
export const Ghost: Story<ButtonProps> = { args: { tone: "ghost", children: "Dismiss" } };
export const Danger: Story<ButtonProps> = { args: { tone: "danger", children: "Delete application" } };

export const Small: Story<ButtonProps> = { args: { tone: "primary", size: "sm", children: "Save changes" } };
export const Large: Story<ButtonProps> = { args: { tone: "primary", size: "lg", children: "Save changes" } };

export const Loading: Story<ButtonProps> = { args: { tone: "primary", loading: true, children: "Saving\u2026" } };

// Real SVG placeholders, not literal `+`/`→` characters — a corrected wrong
// turn: the original text-glyph version left axe's `color-contrast` rule
// unable to classify a `aria-hidden` span whose only content was a
// non-letter character ("Element content contains only non-text
// characters"), reported as an unmeasurable "incomplete" result rather
// than a pass. A real icon has no text content for that rule to trip on.
export const WithIcons: Story<ButtonProps> = {
    args: {
        tone: "primary",
        leadingIcon: <PlusGlyph />,
        trailingIcon: <ArrowRightGlyph />,
        children: "Add job",
    },
};

export const AsAnchor: Story<ButtonProps> = {
    args: {
        tone: "primary",
        render: <a href="/jobs" />,
        nativeButton: false,
        children: "View jobs",
    },
};
