import { Switch, type SwitchProps } from "./Switch";

/** See `field/Field.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<SwitchProps> = {
    title: "Inputs/Switch",
    component: Switch,
};
export default meta;

export const Off: Story<SwitchProps> = {
    args: {
        "aria-label": "Email reminders",
    },
};

export const On: Story<SwitchProps> = {
    args: {
        "aria-label": "Email reminders",
        defaultChecked: true,
    },
};

export const Invalid: Story<SwitchProps> = {
    args: {
        "aria-label": "Email reminders",
        "aria-invalid": "true",
    },
};

export const Disabled: Story<SwitchProps> = {
    args: {
        "aria-label": "Email reminders",
        disabled: true,
    },
};

export const DisabledOn: Story<SwitchProps> = {
    args: {
        "aria-label": "Email reminders",
        disabled: true,
        defaultChecked: true,
    },
};
