import { Stat, type StatProps } from "./Stat";
import { Numeric } from "../numeric";
import { Money } from "../money";
import { Text } from "../text";

/** See `panel/Panel.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<StatProps> = {
    title: "Compounds/Stat",
    component: Stat,
};
export default meta;

export const CountValue: Story<StatProps> = {
    args: {
        label: "Applications this week",
        value: <Numeric>{"12"}</Numeric>,
    },
};

export const MoneyValue: Story<StatProps> = {
    args: {
        label: "Average signing bonus",
        value: <Money cents={1250000} />,
    },
};

export const SuccessDelta: Story<StatProps> = {
    args: {
        label: "Applications this week",
        value: <Numeric>{"12"}</Numeric>,
        delta: { value: "+12% vs last week", tone: "success" },
    },
};

export const DangerDelta: Story<StatProps> = {
    args: {
        label: "Rejections this week",
        value: <Numeric>{"4"}</Numeric>,
        delta: { value: "+3 vs last week", tone: "danger" },
    },
};

export const NeutralDelta: Story<StatProps> = {
    args: {
        label: "Open applications",
        value: <Numeric>{"27"}</Numeric>,
        delta: { value: "No change", tone: "neutral" },
    },
};

/** `value` is `ReactNode`, not necessarily numeric — a caller may compose any pre-styled content. */
export const NonNumericValue: Story<StatProps> = {
    args: {
        label: "Most active source",
        value: <Text variant="title2">LinkedIn</Text>,
    },
};
