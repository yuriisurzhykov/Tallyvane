import { useState } from "react";
import { MultiSelect } from "./MultiSelect";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning. `MultiSelect` has no single `component` function to hand
 * `args` to — it is a compound object assembled differently per story, the
 * same shape `Combobox.stories.tsx` already uses for the same reason, so
 * every story below uses CSF3's `render` form instead of `args`.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof MultiSelect.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const TECH_TAGS = ["React", "Vue", "Angular", "Svelte", "TypeScript", "GraphQL", "Rust", "Go"];
const US_STATES = ["California", "New York", "Texas", "Washington", "Colorado"];

function TagPicker({ items, defaultValue }: { readonly items: readonly string[]; readonly defaultValue: string[] }) {
    const [value, setValue] = useState<string[]>(defaultValue);
    const inputId = `multiselect-${ items[0] }`;

    return (
        <MultiSelect.Root items={ items } value={ value } onValueChange={ setValue }>
            <label htmlFor={ inputId }>Tech tags</label>
            <MultiSelect.InputGroup>
                <MultiSelect.Chips>
                    <MultiSelect.Value>
                        { (selected: string[]) =>
                            selected.map((item) => (
                                <MultiSelect.Chip key={ item }>
                                    { item }
                                    <MultiSelect.ChipRemove label={ `Remove ${ item }` }/>
                                </MultiSelect.Chip>
                            ))
                        }
                    </MultiSelect.Value>
                    <MultiSelect.Input id={ inputId } placeholder={ value.length > 0 ? "" : "Add a tag" }/>
                </MultiSelect.Chips>
                <MultiSelect.Clear label="Clear all tags"/>
                <MultiSelect.Trigger label="Show all tags"/>
            </MultiSelect.InputGroup>
            <MultiSelect.Popup>
                <MultiSelect.Empty>No tags found</MultiSelect.Empty>
                <MultiSelect.List>
                    { (item: string) => (
                        <MultiSelect.Item key={ item } value={ item }>
                            { item }
                        </MultiSelect.Item>
                    ) }
                </MultiSelect.List>
            </MultiSelect.Popup>
        </MultiSelect.Root>
    );
}

const meta: StoryMeta = {
    title: "Inputs/MultiSelect",
    component: MultiSelect.Root,
};
export default meta;

export const Default: Story = {
    render: () => <TagPicker items={ TECH_TAGS } defaultValue={ ["React", "TypeScript"] }/>,
};

export const Empty: Story = {
    render: () => <TagPicker items={ TECH_TAGS } defaultValue={ [] }/>,
};

export const ManyChipsWrap: Story = {
    render: () => <TagPicker items={ TECH_TAGS } defaultValue={ TECH_TAGS.slice(0, 6) }/>,
};

export const Tones: Story = {
    render: () => (
        <div className="flex flex-wrap gap-inline-tight">
            { (["neutral", "info", "attention", "success", "danger"] as const).map((tone) => (
                <MultiSelect.Chip key={ tone } tone={ tone }>
                    { tone }
                    <MultiSelect.ChipRemove label={ `Remove ${ tone }` }/>
                </MultiSelect.Chip>
            )) }
        </div>
    ),
};

export const AllowedStates: Story = {
    render: () => <TagPicker items={ US_STATES } defaultValue={ ["California"] }/>,
};

export const Disabled: Story = {
    render: () => (
        <MultiSelect.Root items={ TECH_TAGS } defaultValue={ ["React"] } disabled>
            <label htmlFor="tags-disabled">Tech tags</label>
            <MultiSelect.InputGroup>
                <MultiSelect.Chips>
                    <MultiSelect.Value>
                        { (selected: string[]) =>
                            selected.map((item) => (
                                <MultiSelect.Chip key={ item }>
                                    { item }
                                    <MultiSelect.ChipRemove label={ `Remove ${ item }` }/>
                                </MultiSelect.Chip>
                            ))
                        }
                    </MultiSelect.Value>
                    <MultiSelect.Input id="tags-disabled" placeholder="Add a tag"/>
                </MultiSelect.Chips>
                <MultiSelect.Trigger label="Show all tags"/>
            </MultiSelect.InputGroup>
            <MultiSelect.Popup>
                <MultiSelect.List>
                    { (item: string) => (
                        <MultiSelect.Item key={ item } value={ item }>
                            { item }
                        </MultiSelect.Item>
                    ) }
                </MultiSelect.List>
            </MultiSelect.Popup>
        </MultiSelect.Root>
    ),
};
