import { RatingScale, type RatingScaleProps } from "./RatingScale";

/** See `field/Field.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const getValueLabel = (value: number) => `${ value } of 5`;

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
};

export const Rated: Story<RatingScaleProps> = {
    args: {
        label: "Interest level",
        getValueLabel,
        defaultValue: 4,
    },
};

export const Disabled: Story<RatingScaleProps> = {
    args: {
        label: "Interest level",
        getValueLabel,
        defaultValue: 3,
        disabled: true,
    },
};
