import { Checkbox, type CheckboxProps } from "./Checkbox";

/** See `field/Field.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
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
};

export const Checked: Story<CheckboxProps> = {
    args: {
        "aria-label": "Remote only",
        defaultChecked: true,
    },
};

export const Indeterminate: Story<CheckboxProps> = {
    args: {
        "aria-label": "Select all",
        indeterminate: true,
    },
};

export const Invalid: Story<CheckboxProps> = {
    args: {
        "aria-label": "Accept the terms",
        "aria-invalid": "true",
    },
};

export const Disabled: Story<CheckboxProps> = {
    args: {
        "aria-label": "Remote only",
        disabled: true,
    },
};

export const DisabledChecked: Story<CheckboxProps> = {
    args: {
        "aria-label": "Remote only",
        disabled: true,
        defaultChecked: true,
    },
};
