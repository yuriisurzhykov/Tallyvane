import { Toggle } from "./Toggle";

/** See `button/Button.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Toggle;
}

interface Story {
    readonly args: {
        readonly defaultPressed?: boolean;
        readonly disabled?: boolean;
        readonly children: string;
    };
}

const meta: StoryMeta = {
    title: "Actions/Toggle",
    component: Toggle,
};
export default meta;

export const Unpressed: Story = { args: { children: "Table view" } };
export const Pressed: Story = { args: { defaultPressed: true, children: "Table view" } };
export const Disabled: Story = { args: { disabled: true, children: "Table view" } };
