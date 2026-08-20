import { Checkbox, type CheckboxProps } from "./Checkbox";

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

const meta: StoryMeta<CheckboxProps> = {
    title: "Inputs/Checkbox",
    component: Checkbox,
};
export default meta;

export const Unchecked: Story<CheckboxProps> = {
    args: {
        "aria-label": "Remote only",
    },
    tags: ["no-visible-text"],
};

export const Checked: Story<CheckboxProps> = {
    args: {
        "aria-label": "Remote only",
        defaultChecked: true,
    },
    tags: ["no-visible-text"],
};

export const Indeterminate: Story<CheckboxProps> = {
    args: {
        "aria-label": "Select all",
        indeterminate: true,
    },
    tags: ["no-visible-text"],
};

export const Invalid: Story<CheckboxProps> = {
    args: {
        "aria-label": "Accept the terms",
        "aria-invalid": "true",
    },
    tags: ["no-visible-text"],
};

export const Disabled: Story<CheckboxProps> = {
    args: {
        "aria-label": "Remote only",
        disabled: true,
    },
    tags: ["no-visible-text"],
};

export const DisabledChecked: Story<CheckboxProps> = {
    args: {
        "aria-label": "Remote only",
        disabled: true,
        defaultChecked: true,
    },
    tags: ["no-visible-text"],
};
