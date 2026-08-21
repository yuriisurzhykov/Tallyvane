import { RatingScale, type RatingScaleProps } from "./RatingScale";

/** See `field/Field.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
    /** No visible text — `label`/`getValueLabel` only ever reach the DOM as `aria-label`s on the five dots. Opts out of the APCA suite's text-contrast check, which has nothing to measure here. */
    readonly tags?: readonly string[];
}

const getValueLabel = (value: number) => `${ String(value) } of 5`;

const meta: StoryMeta<RatingScaleProps> = {
    title: "Inputs/RatingScale",
    component: RatingScale,
};
export default meta;

export const Unrated: Story<RatingScaleProps> = {
    args: {
        label: "Interest level",
        getValueLabel,
    },
    tags: ["no-visible-text"],
};

export const Rated: Story<RatingScaleProps> = {
    args: {
        label: "Interest level",
        getValueLabel,
        defaultValue: 4,
    },
    tags: ["no-visible-text"],
};

export const Disabled: Story<RatingScaleProps> = {
    args: {
        label: "Interest level",
        getValueLabel,
        defaultValue: 3,
        disabled: true,
    },
    tags: ["no-visible-text"],
};
