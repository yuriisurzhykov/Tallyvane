import { ToggleGroup, type ToggleGroupProps } from "./ToggleGroup";
import { Toggle } from "../toggle";

/** See `field/Field.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<ToggleGroupProps> = {
    title: "Actions/ToggleGroup",
    component: ToggleGroup,
};
export default meta;

// `children` passed directly in `args`, the same way `Field.stories.tsx`
// passes its control — the two `Toggle`s are the whole point of this story.
export const ExclusiveChoice: Story<ToggleGroupProps> = {
    args: {
        defaultValue: ["table"],
        children: (
            <>
                <Toggle value="table">Table</Toggle>
                <Toggle value="board">Board</Toggle>
            </>
        ),
    },
};

export const MultipleSelection: Story<ToggleGroupProps> = {
    args: {
        multiple: true,
        defaultValue: ["table"],
        children: (
            <>
                <Toggle value="table">Table</Toggle>
                <Toggle value="board">Board</Toggle>
            </>
        ),
    },
};

export const Vertical: Story<ToggleGroupProps> = {
    args: {
        orientation: "vertical",
        defaultValue: ["table"],
        children: (
            <>
                <Toggle value="table">Table</Toggle>
                <Toggle value="board">Board</Toggle>
            </>
        ),
    },
};
