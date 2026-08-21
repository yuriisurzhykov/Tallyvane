import { Callout, type CalloutProps } from "./Callout";

/** See `badge/Badge.stories.tsx` for why this local shape stands in for CSF3's real types. */
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
            <path d="M8 1v9M8 13v2" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round"/>
        </svg>
    );
}

const meta: StoryMeta<CalloutProps> = {
    title: "Status and feedback/Callout",
    component: Callout,
};
export default meta;

export const Neutral: Story<CalloutProps> = { args: { tone: "neutral", children: "This field is optional." } };
export const Info: Story<CalloutProps> = {
    args: {
        tone: "info",
        leadingIcon: <PlaceholderIcon/>,
        children: "Extraction confidence is moderate — review the parsed fields before saving."
    },
};
export const Attention: Story<CalloutProps> = {
    args: { tone: "attention", leadingIcon: <PlaceholderIcon/>, children: "This month's LLM budget is 80% spent." },
};
export const Success: Story<CalloutProps> = {
    args: {
        tone: "success",
        children: "Your résumé passed every ATS check."
    }
};
export const Danger: Story<CalloutProps> = {
    args: {
        tone: "danger",
        leadingIcon: <PlaceholderIcon/>,
        children: "This bonus is taxed as supplemental income at a higher withholding rate."
    },
};
