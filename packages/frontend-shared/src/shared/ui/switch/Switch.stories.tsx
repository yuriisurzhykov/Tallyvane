import { Switch, type SwitchProps } from "./Switch";

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

const meta: StoryMeta<SwitchProps> = {
    title: "Inputs/Switch",
    component: Switch,
};
export default meta;

export const Off: Story<SwitchProps> = {
    args: {
        "aria-label": "Email reminders",
    },
    tags: ["no-visible-text"],
};

export const On: Story<SwitchProps> = {
    args: {
        "aria-label": "Email reminders",
        defaultChecked: true,
    },
    tags: ["no-visible-text"],
};

export const Invalid: Story<SwitchProps> = {
    args: {
        "aria-label": "Email reminders",
        "aria-invalid": "true",
    },
    tags: ["no-visible-text"],
};

export const Disabled: Story<SwitchProps> = {
    args: {
        "aria-label": "Email reminders",
        disabled: true,
    },
    tags: ["no-visible-text"],
};

export const DisabledOn: Story<SwitchProps> = {
    args: {
        "aria-label": "Email reminders",
        disabled: true,
        defaultChecked: true,
    },
    tags: ["no-visible-text"],
};
