import { Autocomplete } from "./Autocomplete";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning behind this local shape. `Autocomplete` has no single
 * `component` function to hand `args` to — it is a compound object assembled
 * differently per story, the same shape `Combobox.stories.tsx` already uses
 * for its own compound field.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Autocomplete.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const JOB_TITLES = ["Software Engineer", "Senior Software Engineer", "Staff Engineer", "Engineering Manager", "Product Manager"];
const RECENT_GROUPS = [
    { value: "Recent searches", items: ["Software Engineer", "Product Manager"] },
    { value: "All titles", items: JOB_TITLES },
];

const meta: StoryMeta = {
    title: "Inputs/Autocomplete",
    component: Autocomplete.Root,
};
export default meta;

export const Default: Story = {
    render: () => (
        <Autocomplete.Root items={ JOB_TITLES }>
            <label htmlFor="autocomplete-title">Job title</label>
            <Autocomplete.InputGroup>
                <Autocomplete.Input id="autocomplete-title" placeholder="e.g. Software Engineer"/>
                <Autocomplete.Clear label="Clear job title"/>
                <Autocomplete.Trigger label="Show suggested titles"/>
            </Autocomplete.InputGroup>
            <Autocomplete.Popup>
                <Autocomplete.Empty>No matching titles — your own text is still a valid value</Autocomplete.Empty>
                <Autocomplete.List>
                    { (title: string) => (
                        <Autocomplete.Item key={ title } value={ title }>
                            { title }
                        </Autocomplete.Item>
                    ) }
                </Autocomplete.List>
            </Autocomplete.Popup>
        </Autocomplete.Root>
    ),
};

export const WithDefaultValue: Story = {
    render: () => (
        <Autocomplete.Root items={ JOB_TITLES } defaultValue="Staff Engineer">
            <label htmlFor="autocomplete-title-default">Job title</label>
            <Autocomplete.InputGroup>
                <Autocomplete.Input id="autocomplete-title-default" placeholder="e.g. Software Engineer"/>
                <Autocomplete.Clear label="Clear job title"/>
                <Autocomplete.Trigger label="Show suggested titles"/>
            </Autocomplete.InputGroup>
            <Autocomplete.Popup>
                <Autocomplete.List>
                    { (title: string) => (
                        <Autocomplete.Item key={ title } value={ title }>
                            { title }
                        </Autocomplete.Item>
                    ) }
                </Autocomplete.List>
            </Autocomplete.Popup>
        </Autocomplete.Root>
    ),
};

export const FreeTextNotInList: Story = {
    render: () => (
        <Autocomplete.Root items={ JOB_TITLES } defaultValue="Head of Platform Engineering">
            <label htmlFor="autocomplete-title-freetext">Job title</label>
            <Autocomplete.InputGroup>
                <Autocomplete.Input id="autocomplete-title-freetext" placeholder="e.g. Software Engineer"/>
                <Autocomplete.Clear label="Clear job title"/>
                <Autocomplete.Trigger label="Show suggested titles"/>
            </Autocomplete.InputGroup>
            <Autocomplete.Popup>
                <Autocomplete.Empty>No matching titles — your own text is still a valid value</Autocomplete.Empty>
                <Autocomplete.List>
                    { (title: string) => (
                        <Autocomplete.Item key={ title } value={ title }>
                            { title }
                        </Autocomplete.Item>
                    ) }
                </Autocomplete.List>
            </Autocomplete.Popup>
        </Autocomplete.Root>
    ),
};

export const Sizes: Story = {
    render: () => (
        <div className="flex flex-col items-start gap-stack">
            { (["sm", "md", "lg"] as const).map((size) => (
                <Autocomplete.Root key={ size } items={ JOB_TITLES }>
                    <label htmlFor={ `autocomplete-title-${ size }` }>{ `Job title (${ size })` }</label>
                    <Autocomplete.InputGroup size={ size }>
                        <Autocomplete.Input id={ `autocomplete-title-${ size }` } placeholder="e.g. Software Engineer"/>
                        <Autocomplete.Clear label="Clear job title"/>
                        <Autocomplete.Trigger label="Show suggested titles"/>
                    </Autocomplete.InputGroup>
                    <Autocomplete.Popup>
                        <Autocomplete.List>
                            { (title: string) => (
                                <Autocomplete.Item key={ title } value={ title }>
                                    { title }
                                </Autocomplete.Item>
                            ) }
                        </Autocomplete.List>
                    </Autocomplete.Popup>
                </Autocomplete.Root>
            )) }
        </div>
    ),
};

export const Grouped: Story = {
    render: () => (
        <Autocomplete.Root items={ RECENT_GROUPS }>
            <label htmlFor="autocomplete-title-grouped">Job title</label>
            <Autocomplete.InputGroup>
                <Autocomplete.Input id="autocomplete-title-grouped" placeholder="e.g. Software Engineer"/>
                <Autocomplete.Clear label="Clear job title"/>
                <Autocomplete.Trigger label="Show suggested titles"/>
            </Autocomplete.InputGroup>
            <Autocomplete.Popup>
                <Autocomplete.List>
                    { RECENT_GROUPS.map((group, index) => (
                        <div key={ group.value }>
                            <Autocomplete.Group>
                                <Autocomplete.GroupLabel>{ group.value }</Autocomplete.GroupLabel>
                                { group.items.map((title) => (
                                    <Autocomplete.Item key={ title } value={ title }>
                                        { title }
                                    </Autocomplete.Item>
                                )) }
                            </Autocomplete.Group>
                            { index < RECENT_GROUPS.length - 1 ? <Autocomplete.Separator/> : null }
                        </div>
                    )) }
                </Autocomplete.List>
            </Autocomplete.Popup>
        </Autocomplete.Root>
    ),
};

export const Disabled: Story = {
    render: () => (
        <Autocomplete.Root items={ JOB_TITLES } disabled>
            <label htmlFor="autocomplete-title-disabled">Job title</label>
            <Autocomplete.InputGroup>
                <Autocomplete.Input id="autocomplete-title-disabled" placeholder="e.g. Software Engineer"/>
                <Autocomplete.Clear label="Clear job title"/>
                <Autocomplete.Trigger label="Show suggested titles"/>
            </Autocomplete.InputGroup>
            <Autocomplete.Popup>
                <Autocomplete.List>
                    { (title: string) => (
                        <Autocomplete.Item key={ title } value={ title }>
                            { title }
                        </Autocomplete.Item>
                    ) }
                </Autocomplete.List>
            </Autocomplete.Popup>
        </Autocomplete.Root>
    ),
};
