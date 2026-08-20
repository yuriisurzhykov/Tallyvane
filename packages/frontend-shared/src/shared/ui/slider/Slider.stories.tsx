import { Slider, type SliderProps } from "./Slider";

/** See `field/Field.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<SliderProps> = {
    title: "Inputs/Slider",
    component: Slider,
};
export default meta;

export const Default: Story<SliderProps> = {
    args: {
        "aria-label": "Weekly application goal",
        defaultValue: 5,
        min: 0,
        max: 20,
    },
};

export const AtMinimum: Story<SliderProps> = {
    args: {
        "aria-label": "Weekly application goal",
        defaultValue: 0,
        min: 0,
        max: 20,
    },
};

export const AtMaximum: Story<SliderProps> = {
    args: {
        "aria-label": "Weekly application goal",
        defaultValue: 20,
        min: 0,
        max: 20,
    },
};

export const Disabled: Story<SliderProps> = {
    args: {
        "aria-label": "Weekly application goal",
        defaultValue: 5,
        min: 0,
        max: 20,
        disabled: true,
    },
};
