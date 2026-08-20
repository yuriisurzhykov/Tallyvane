import { MoneyField } from "./MoneyField";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning. `MoneyField` has no single `component` function to hand
 * `args` to — it is a compound object assembled differently per story, the
 * same shape `NumberField.stories.tsx` already uses for the same reason, so
 * every story below uses CSF3's `render` form instead of `args`.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof MoneyField.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Inputs/MoneyField",
    component: MoneyField.Root,
};
export default meta;

export const Default: Story = {
    render: () => (
        <MoneyField.Root id="stated-salary" locale="en-US" defaultValue={ 9500000 }>
            <MoneyField.Label htmlFor="stated-salary">Stated salary</MoneyField.Label>
            <MoneyField.Group>
                <MoneyField.Decrement label="Decrease stated salary"/>
                <MoneyField.Input/>
                <MoneyField.Increment label="Increase stated salary"/>
            </MoneyField.Group>
        </MoneyField.Root>
    ),
};

export const Sizes: Story = {
    render: () => (
        <div className="flex flex-col items-start gap-stack">
            { (["sm", "md", "lg"] as const).map((size) => (
                <MoneyField.Root key={ size } id={ `fee-${ size }` } locale="en-US" defaultValue={ 2500 }>
                    <MoneyField.Label htmlFor={ `fee-${ size }` }>{ `Fee (${ size })` }</MoneyField.Label>
                    <MoneyField.Group size={ size }>
                        <MoneyField.Decrement label="Decrease fee" size={ size }/>
                        <MoneyField.Input/>
                        <MoneyField.Increment label="Increase fee" size={ size }/>
                    </MoneyField.Group>
                </MoneyField.Root>
            )) }
        </div>
    ),
};

export const WithMinMax: Story = {
    render: () => (
        <MoneyField.Root id="bonus" locale="en-US" defaultValue={ 0 } min={ 0 } max={ 5000000 } step={ 1000 }>
            <MoneyField.Label htmlFor="bonus">Signing bonus (up to $50,000)</MoneyField.Label>
            <MoneyField.Group>
                <MoneyField.Decrement label="Decrease signing bonus"/>
                <MoneyField.Input/>
                <MoneyField.Increment label="Increase signing bonus"/>
            </MoneyField.Group>
        </MoneyField.Root>
    ),
};

export const NonUsdCurrency: Story = {
    render: () => (
        <MoneyField.Root id="relocation-fee" locale="en-US" currency="EUR" defaultValue={ 150000 }>
            <MoneyField.Label htmlFor="relocation-fee">Relocation stipend (EUR)</MoneyField.Label>
            <MoneyField.Group>
                <MoneyField.Decrement label="Decrease relocation stipend"/>
                <MoneyField.Input/>
                <MoneyField.Increment label="Increase relocation stipend"/>
            </MoneyField.Group>
        </MoneyField.Root>
    ),
};

export const Disabled: Story = {
    render: () => (
        <MoneyField.Root id="salary-disabled" locale="en-US" defaultValue={ 8000000 } disabled>
            <MoneyField.Label htmlFor="salary-disabled">Stated salary</MoneyField.Label>
            <MoneyField.Group>
                <MoneyField.Decrement label="Decrease stated salary"/>
                <MoneyField.Input/>
                <MoneyField.Increment label="Increase stated salary"/>
            </MoneyField.Group>
        </MoneyField.Root>
    ),
};
