import { Radio, type RadioProps } from "./Radio";

/** See `field/Field.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
    /** No visible text — named via `aria-label` alone. Opts out of the APCA suite's text-contrast check, which has nothing to measure here. */
    readonly tags?: readonly string[];
}

const meta: StoryMeta<RadioProps> = {
    title: "Inputs/Radio",
    component: Radio,
};
export default meta;

// A bare `Radio` has no `checked` prop of its own — per `RadioRoot.js`,
// checked state is always derived from comparing this value against a
// `RadioGroup`'s own selected value, never set directly. A "Selected"
// variant genuinely cannot be demonstrated on this component in isolation;
// see `RadioGroup.stories.tsx`'s `WorkMode` story for the real, selected
// multi-option shape.
export const Unselected: Story<RadioProps> = {
    args: {
        "aria-label": "Remote",
        value: "remote",
    },
    tags: ["no-visible-text"],
};

export const Invalid: Story<RadioProps> = {
    args: {
        "aria-label": "Remote",
        value: "remote",
        "aria-invalid": "true",
    },
    tags: ["no-visible-text"],
};

export const Disabled: Story<RadioProps> = {
    args: {
        "aria-label": "Remote",
        value: "remote",
        disabled: true,
    },
    tags: ["no-visible-text"],
};
