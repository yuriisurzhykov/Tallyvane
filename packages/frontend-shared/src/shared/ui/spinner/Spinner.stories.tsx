import { Spinner, type SpinnerProps } from "./Spinner";

/** See `badge/Badge.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<SpinnerProps> = {
    title: "Status and feedback/Spinner",
    component: Spinner,
};
export default meta;

export const Small: Story<SpinnerProps> = { args: { size: "sm" } };
export const Medium: Story<SpinnerProps> = { args: { size: "md" } };
export const Large: Story<SpinnerProps> = { args: { size: "lg" } };
export const WithLabel: Story<SpinnerProps> = { args: { size: "lg", label: "Rendering PDF\u2026" } };
