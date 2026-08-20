import { Skeleton, type SkeletonProps } from "./Skeleton";

/** See `badge/Badge.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
    /** No visible text — a placeholder shape carries no content by definition. Opts out of the APCA suite's text-contrast check, which has nothing to measure here. */
    readonly tags?: readonly string[];
}

const meta: StoryMeta<SkeletonProps> = {
    title: "Status and feedback/Skeleton",
    component: Skeleton,
};
export default meta;

export const TextLine: Story<SkeletonProps> = { args: {}, tags: ["no-visible-text"] };
export const NarrowLine: Story<SkeletonProps> = { args: { className: "w-(--control-height-lg)" }, tags: ["no-visible-text"] };
export const AvatarShaped: Story<SkeletonProps> = { args: { className: "h-(--control-height-lg) w-(--control-height-lg) rounded-pill" }, tags: ["no-visible-text"] };
export const CardShaped: Story<SkeletonProps> = {
    args: {
        className: "h-(--control-height-lg) rounded-card",
        style: { width: 320 }
    },
    tags: ["no-visible-text"],
};
