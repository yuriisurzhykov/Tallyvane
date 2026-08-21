import { KeyValueList, type KeyValueListProps } from "./KeyValueList";
import { Text } from "../text";
import { Money } from "../money";
import { Badge } from "../badge";

/** See `panel/Panel.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
    /** See `dot/Dot.stories.tsx` for why a story with no visible text opts out of the APCA suite's text-contrast check. */
    readonly tags?: readonly string[];
}

const meta: StoryMeta<KeyValueListProps> = {
    title: "Compounds/KeyValueList",
    component: KeyValueList,
};
export default meta;

/**
 * `value` is `ReactNode` and is never re-styled by this component (`KeyValueList.tsx`'s
 * own doc comment) — every plain-string demo value below is wrapped in `Text` here,
 * the same "raw story text needs its own colour class" fix already applied in
 * `panel/Panel.stories.tsx` and `row/Row.stories.tsx`, since a bare string would
 * otherwise reach the APCA/WCAG suites with no colour of its own.
 */
export const ParsedJobFields: Story<KeyValueListProps> = {
    args: {
        items: [
            { label: "Location", value: <Text variant="small">Remote (US)</Text> },
            { label: "Seniority", value: <Text variant="small">Senior</Text> },
            { label: "Work mode", value: <Text variant="small">Remote</Text> },
            { label: "Source", value: <Text variant="small">LinkedIn</Text> },
        ],
    },
};

export const CompensationBreakdown: Story<KeyValueListProps> = {
    args: {
        items: [
            { label: "Base salary", value: <Money cents={15000000} /> },
            { label: "Signing bonus", value: <Money cents={1000000} /> },
            { label: "Annual bonus target", value: <Money cents={2000000} /> },
        ],
    },
};

export const WithBadgeValues: Story<KeyValueListProps> = {
    args: {
        items: [
            {
                label: "Status",
                value: (
                    <Badge tone="success" treatment="subtle">
                        Active
                    </Badge>
                ),
            },
            {
                label: "Priority",
                value: (
                    <Badge tone="attention" treatment="subtle">
                        High
                    </Badge>
                ),
            },
        ],
    },
};

export const Empty: Story<KeyValueListProps> = {
    args: { items: [] },
    tags: ["no-visible-text"],
};
