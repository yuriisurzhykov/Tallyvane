import { Meter, type MeterProps } from "./Meter";

/** See `badge/Badge.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<MeterProps> = {
    title: "Status and feedback/Meter",
    component: Meter,
};
export default meta;

export const LowInRange: Story<MeterProps> = {
    args: {
        label: "Offered salary within range",
        value: 62000,
        min: 60000,
        max: 90000
    }
};
export const MidRange: Story<MeterProps> = {
    args: {
        label: "Offered salary within range",
        value: 75000,
        min: 60000,
        max: 90000
    }
};
export const HighInRange: Story<MeterProps> = {
    args: {
        label: "Offered salary within range",
        value: 88000,
        min: 60000,
        max: 90000
    }
};
