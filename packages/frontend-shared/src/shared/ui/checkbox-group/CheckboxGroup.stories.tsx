import { CheckboxGroup, type CheckboxGroupProps } from "./CheckboxGroup";
import { Checkbox } from "../checkbox";

/** See `field/Field.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
    /** No visible text — every checkbox here is named via `aria-label` alone. Opts out of the APCA suite's text-contrast check, which has nothing to measure here. */
    readonly tags?: readonly string[];
}

const meta: StoryMeta<CheckboxGroupProps> = {
    title: "Inputs/CheckboxGroup",
    component: CheckboxGroup,
};
export default meta;

// `children` passed directly in `args`, the same way `ToggleGroup.stories.tsx` does.
export const TagFilters: Story<CheckboxGroupProps> = {
    args: {
        "aria-label": "Tech tags",
        defaultValue: ["kotlin"],
        children: (
            <>
                <Checkbox aria-label="React" value="react"/>
                <Checkbox aria-label="Kotlin" value="kotlin"/>
                <Checkbox aria-label="SQL" value="sql"/>
            </>
        ),
    },
    tags: ["no-visible-text"],
};

export const SelectAllParent: Story<CheckboxGroupProps> = {
    args: {
        "aria-label": "Tech tags",
        defaultValue: ["react"],
        allValues: ["react", "kotlin", "sql"],
        children: (
            <>
                <Checkbox aria-label="Select all" parent/>
                <Checkbox aria-label="React" value="react"/>
                <Checkbox aria-label="Kotlin" value="kotlin"/>
                <Checkbox aria-label="SQL" value="sql"/>
            </>
        ),
    },
    tags: ["no-visible-text"],
};
