import { Spinner, type SpinnerProps } from "./Spinner";

/** See `badge/Badge.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
    /** No visible text — with no `label`, only the `aria-hidden` ring renders. Opts out of the APCA suite's text-contrast check, which has nothing to measure here. */
    readonly tags?: readonly string[];
}

const meta: StoryMeta<SpinnerProps> = {
    title: "Status and feedback/Spinner",
    component: Spinner,
};
export default meta;

export const Small: Story<SpinnerProps> = { args: { size: "sm" }, tags: ["no-visible-text"] };
export const Medium: Story<SpinnerProps> = { args: { size: "md" }, tags: ["no-visible-text"] };
export const Large: Story<SpinnerProps> = { args: { size: "lg" }, tags: ["no-visible-text"] };
export const WithLabel: Story<SpinnerProps> = { args: { size: "lg", label: "Rendering PDF\u2026" } };
