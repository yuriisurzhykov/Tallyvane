import { Skeleton, type SkeletonProps } from "./Skeleton";

/** See `badge/Badge.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<SkeletonProps> = {
    title: "Status and feedback/Skeleton",
    component: Skeleton,
};
export default meta;

export const TextLine: Story<SkeletonProps> = { args: {} };
export const NarrowLine: Story<SkeletonProps> = { args: { className: "w-(--control-height-lg)" } };
export const AvatarShaped: Story<SkeletonProps> = { args: { className: "h-(--control-height-lg) w-(--control-height-lg) rounded-pill" } };
export const CardShaped: Story<SkeletonProps> = {
    args: {
        className: "h-(--control-height-lg) rounded-card",
        style: { width: 320 }
    }
};
