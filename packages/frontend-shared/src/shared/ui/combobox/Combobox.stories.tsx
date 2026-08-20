import { Combobox } from "./Combobox";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning behind this local shape. `Combobox` has no single
 * `component` function to hand `args` to — it is a compound object assembled
 * differently per story, the same shape `Select.stories.tsx` already uses
 * for its own compound field.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Combobox.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const COMPANIES = ["Acme Corp", "Globex", "Initech", "Umbrella Corp", "Wayne Enterprises", "Stark Industries"];
const RECENT_GROUPS = [
    { value: "Recent", items: ["Acme Corp", "Globex"] },
    { value: "All companies", items: COMPANIES },
];

const meta: StoryMeta = {
    title: "Inputs/Combobox",
    component: Combobox.Root,
};
export default meta;

export const Default: Story = {
    render: () => (
        <Combobox.Root items={ COMPANIES }>
            <Combobox.Label htmlFor="combobox-company">Company</Combobox.Label>
            <Combobox.InputGroup>
                <Combobox.Input id="combobox-company" placeholder="Search companies"/>
                <Combobox.Clear label="Clear company"/>
                <Combobox.Trigger label="Show all companies"/>
            </Combobox.InputGroup>
            <Combobox.Popup>
                <Combobox.Empty>No companies found</Combobox.Empty>
                <Combobox.List>
                    { (company: string) => (
                        <Combobox.Item key={ company } value={ company }>
                            { company }
                        </Combobox.Item>
                    ) }
                </Combobox.List>
            </Combobox.Popup>
        </Combobox.Root>
    ),
};

export const WithDefaultValue: Story = {
    render: () => (
        <Combobox.Root items={ COMPANIES } defaultValue="Globex">
            <Combobox.Label htmlFor="combobox-company-default">Company</Combobox.Label>
            <Combobox.InputGroup>
                <Combobox.Input id="combobox-company-default" placeholder="Search companies"/>
                <Combobox.Clear label="Clear company"/>
                <Combobox.Trigger label="Show all companies"/>
            </Combobox.InputGroup>
            <Combobox.Popup>
                <Combobox.Empty>No companies found</Combobox.Empty>
                <Combobox.List>
                    { (company: string) => (
                        <Combobox.Item key={ company } value={ company }>
                            { company }
                        </Combobox.Item>
                    ) }
                </Combobox.List>
            </Combobox.Popup>
        </Combobox.Root>
    ),
};

export const Sizes: Story = {
    render: () => (
        <div className="flex flex-col items-start gap-stack">
            { (["sm", "md", "lg"] as const).map((size) => (
                <Combobox.Root key={ size } items={ COMPANIES }>
                    <Combobox.Label htmlFor={ `combobox-company-${ size }` }>{ `Company (${ size })` }</Combobox.Label>
                    <Combobox.InputGroup size={ size }>
                        <Combobox.Input id={ `combobox-company-${ size }` } placeholder="Search companies"/>
                        <Combobox.Clear label="Clear company"/>
                        <Combobox.Trigger label="Show all companies"/>
                    </Combobox.InputGroup>
                    <Combobox.Popup>
                        <Combobox.Empty>No companies found</Combobox.Empty>
                        <Combobox.List>
                            { (company: string) => (
                                <Combobox.Item key={ company } value={ company }>
                                    { company }
                                </Combobox.Item>
                            ) }
                        </Combobox.List>
                    </Combobox.Popup>
                </Combobox.Root>
            )) }
        </div>
    ),
};

export const Grouped: Story = {
    render: () => (
        <Combobox.Root items={ RECENT_GROUPS }>
            <Combobox.Label htmlFor="combobox-company-grouped">Company</Combobox.Label>
            <Combobox.InputGroup>
                <Combobox.Input id="combobox-company-grouped" placeholder="Search companies"/>
                <Combobox.Clear label="Clear company"/>
                <Combobox.Trigger label="Show all companies"/>
            </Combobox.InputGroup>
            <Combobox.Popup>
                <Combobox.Empty>No companies found</Combobox.Empty>
                <Combobox.List>
                    { RECENT_GROUPS.map((group, index) => (
                        <div key={ group.value }>
                            <Combobox.Group>
                                <Combobox.GroupLabel>{ group.value }</Combobox.GroupLabel>
                                { group.items.map((company) => (
                                    <Combobox.Item key={ company } value={ company }>
                                        { company }
                                    </Combobox.Item>
                                )) }
                            </Combobox.Group>
                            { index < RECENT_GROUPS.length - 1 ? <Combobox.Separator/> : null }
                        </div>
                    )) }
                </Combobox.List>
            </Combobox.Popup>
        </Combobox.Root>
    ),
};

export const Disabled: Story = {
    render: () => (
        <Combobox.Root items={ COMPANIES } disabled>
            <Combobox.Label htmlFor="combobox-company-disabled">Company</Combobox.Label>
            <Combobox.InputGroup>
                <Combobox.Input id="combobox-company-disabled" placeholder="Search companies"/>
                <Combobox.Clear label="Clear company"/>
                <Combobox.Trigger label="Show all companies"/>
            </Combobox.InputGroup>
            <Combobox.Popup>
                <Combobox.List>
                    { (company: string) => (
                        <Combobox.Item key={ company } value={ company }>
                            { company }
                        </Combobox.Item>
                    ) }
                </Combobox.List>
            </Combobox.Popup>
        </Combobox.Root>
    ),
};
