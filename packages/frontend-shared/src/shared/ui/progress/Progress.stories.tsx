import { Progress, type ProgressProps } from "./Progress";

/** See `badge/Badge.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<ProgressProps> = {
    title: "Status and feedback/Progress",
    component: Progress,
};
export default meta;

export const Started: Story<ProgressProps> = { args: { label: "Weekly application goal", value: 3, max: 20 } };
export const Halfway: Story<ProgressProps> = { args: { label: "Weekly application goal", value: 10, max: 20 } };
export const Complete: Story<ProgressProps> = { args: { label: "Weekly application goal", value: 20, max: 20 } };
