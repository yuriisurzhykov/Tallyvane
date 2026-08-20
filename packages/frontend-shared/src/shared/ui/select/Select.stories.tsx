import { Select } from "./Select";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning behind this local shape. `Select` has no single `component`
 * function to hand `args` to — it is a compound object assembled differently
 * per story, the same shape `Menu.stories.tsx`/`Popover.stories.tsx` already
 * use for their own compound overlays.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Select.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const WORK_MODES = ["Remote", "Hybrid", "On-site"];
/** `Select.Root`'s own `items` type is `{ label, value }[]` (or `Record`/`Group[]`), never a bare `string[]` — verified against `SelectRoot.d.ts`. */
const WORK_MODE_ITEMS = WORK_MODES.map((mode) => ({ label: mode, value: mode }));
const SENIORITY_GROUPS = [
    { value: "Individual contributor", items: ["Junior", "Mid-level", "Senior", "Staff"] },
    { value: "Management", items: ["Manager", "Director", "VP"] },
];

const meta: StoryMeta = {
    title: "Inputs/Select",
    component: Select.Root,
};
export default meta;

export const Default: Story = {
    render: () => (
        <Select.Root items={ WORK_MODE_ITEMS }>
            <Select.Label>Work mode</Select.Label>
            <Select.Trigger>
                <Select.Value placeholder="Select a work mode"/>
                <Select.Icon/>
            </Select.Trigger>
            <Select.Popup>
                { WORK_MODES.map((mode) => (
                    <Select.Item key={ mode } value={ mode }>
                        { mode }
                    </Select.Item>
                )) }
            </Select.Popup>
        </Select.Root>
    ),
};

export const WithDefaultValue: Story = {
    render: () => (
        <Select.Root items={ WORK_MODE_ITEMS } defaultValue="Hybrid">
            <Select.Label>Work mode</Select.Label>
            <Select.Trigger>
                <Select.Value/>
                <Select.Icon/>
            </Select.Trigger>
            <Select.Popup>
                { WORK_MODES.map((mode) => (
                    <Select.Item key={ mode } value={ mode }>
                        { mode }
                    </Select.Item>
                )) }
            </Select.Popup>
        </Select.Root>
    ),
};

export const Sizes: Story = {
    render: () => (
        <div className="flex flex-col items-start gap-stack">
            { (["sm", "md", "lg"] as const).map((size) => (
                <Select.Root key={ size } items={ WORK_MODE_ITEMS }>
                    <Select.Label>{ `Work mode (${ size })` }</Select.Label>
                    <Select.Trigger size={ size }>
                        <Select.Value placeholder="Select a work mode"/>
                        <Select.Icon/>
                    </Select.Trigger>
                    <Select.Popup>
                        { WORK_MODES.map((mode) => (
                            <Select.Item key={ mode } value={ mode }>
                                { mode }
                            </Select.Item>
                        )) }
                    </Select.Popup>
                </Select.Root>
            )) }
        </div>
    ),
};

export const Grouped: Story = {
    render: () => (
        <Select.Root items={ SENIORITY_GROUPS }>
            <Select.Label>Seniority</Select.Label>
            <Select.Trigger>
                <Select.Value placeholder="Select a seniority level"/>
                <Select.Icon/>
            </Select.Trigger>
            <Select.Popup>
                { SENIORITY_GROUPS.map((group, index) => (
                    <div key={ group.value }>
                        <Select.Group>
                            <Select.GroupLabel>{ group.value }</Select.GroupLabel>
                            { group.items.map((item) => (
                                <Select.Item key={ item } value={ item }>
                                    { item }
                                </Select.Item>
                            )) }
                        </Select.Group>
                        { index < SENIORITY_GROUPS.length - 1 ? <Select.Separator/> : null }
                    </div>
                )) }
            </Select.Popup>
        </Select.Root>
    ),
};

export const Disabled: Story = {
    render: () => (
        <Select.Root items={ WORK_MODE_ITEMS } disabled>
            <Select.Label>Work mode</Select.Label>
            <Select.Trigger>
                <Select.Value placeholder="Select a work mode"/>
                <Select.Icon/>
            </Select.Trigger>
            <Select.Popup>
                { WORK_MODES.map((mode) => (
                    <Select.Item key={ mode } value={ mode }>
                        { mode }
                    </Select.Item>
                )) }
            </Select.Popup>
        </Select.Root>
    ),
};
