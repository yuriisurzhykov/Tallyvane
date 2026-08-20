import { Tag, type TagProps } from "./Tag";

/** See `badge/Badge.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<TagProps> = {
    title: "Status and feedback/Tag",
    component: Tag,
};
export default meta;

export const Neutral: Story<TagProps> = {
    args: {
        tone: "neutral",
        removeLabel: "Remove React",
        children: "React",
        onRemove: () => {
        }
    }
};
export const Info: Story<TagProps> = {
    args: {
        tone: "info",
        removeLabel: "Remove Remote",
        children: "Remote",
        onRemove: () => {
        }
    }
};
export const Attention: Story<TagProps> = {
    args: {
        tone: "attention",
        removeLabel: "Remove no visa sponsorship",
        children: "No visa sponsorship",
        onRemove: () => {
        }
    },
};
export const Success: Story<TagProps> = {
    args: {
        tone: "success",
        removeLabel: "Remove must-have",
        children: "Must-have",
        onRemove: () => {
        }
    }
};
export const Danger: Story<TagProps> = {
    args: {
        tone: "danger",
        removeLabel: "Remove disqualifier",
        children: "Disqualifier",
        onRemove: () => {
        }
    }
};
