import { RadioGroup, type RadioGroupProps } from "./RadioGroup";
import { Radio } from "../radio";

/** See `field/Field.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
    /** No visible text — every radio here is named via `aria-label` alone. Opts out of the APCA suite's text-contrast check, which has nothing to measure here. */
    readonly tags?: readonly string[];
}

const meta: StoryMeta<RadioGroupProps> = {
    title: "Inputs/RadioGroup",
    component: RadioGroup,
};
export default meta;

export const WorkMode: Story<RadioGroupProps> = {
    args: {
        "aria-label": "Work mode",
        defaultValue: "hybrid",
        children: (
            <>
                <Radio aria-label="Remote" value="remote"/>
                <Radio aria-label="Hybrid" value="hybrid"/>
                <Radio aria-label="On-site" value="onsite"/>
            </>
        ),
    },
    tags: ["no-visible-text"],
};

export const Disabled: Story<RadioGroupProps> = {
    args: {
        "aria-label": "Work mode",
        defaultValue: "hybrid",
        disabled: true,
        children: (
            <>
                <Radio aria-label="Remote" value="remote"/>
                <Radio aria-label="Hybrid" value="hybrid"/>
                <Radio aria-label="On-site" value="onsite"/>
            </>
        ),
    },
    tags: ["no-visible-text"],
};
