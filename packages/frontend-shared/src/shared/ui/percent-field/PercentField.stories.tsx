import { PercentField } from "./PercentField";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning. `PercentField` has no single `component` function to hand
 * `args` to — it is a compound object assembled differently per story, the
 * same shape `NumberField.stories.tsx`/`MoneyField.stories.tsx` already use,
 * so every story below uses CSF3's `render` form instead of `args`.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof PercentField.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Inputs/PercentField",
    component: PercentField.Root,
};
export default meta;

export const Default: Story = {
    render: () => (
        <PercentField.Root id="match-rate" locale="en-US" defaultValue={ 500 }>
            <PercentField.Label htmlFor="match-rate">401(k) match rate</PercentField.Label>
            <PercentField.Group>
                <PercentField.Decrement label="Decrease match rate"/>
                <PercentField.Input/>
                <PercentField.Increment label="Increase match rate"/>
            </PercentField.Group>
        </PercentField.Root>
    ),
};

export const Sizes: Story = {
    render: () => (
        <div className="flex flex-col items-start gap-stack">
            { (["sm", "md", "lg"] as const).map((size) => (
                <PercentField.Root key={ size } id={ `vest-${ size }` } locale="en-US" defaultValue={ 250 }>
                    <PercentField.Label htmlFor={ `vest-${ size }` }>{ `Equity vested (${ size })` }</PercentField.Label>
                    <PercentField.Group size={ size }>
                        <PercentField.Decrement label="Decrease equity vested" size={ size }/>
                        <PercentField.Input/>
                        <PercentField.Increment label="Increase equity vested" size={ size }/>
                    </PercentField.Group>
                </PercentField.Root>
            )) }
        </div>
    ),
};

export const OpenEndedOver100Percent: Story = {
    render: () => (
        <PercentField.Root id="match-rate-uncapped" locale="en-US" defaultValue={ 12500 } step={ 100 }>
            <PercentField.Label htmlFor="match-rate-uncapped">401(k) match rate (no cap — some plans exceed 100%)</PercentField.Label>
            <PercentField.Group>
                <PercentField.Decrement label="Decrease match rate"/>
                <PercentField.Input/>
                <PercentField.Increment label="Increase match rate"/>
            </PercentField.Group>
        </PercentField.Root>
    ),
};

export const WithMinMax: Story = {
    render: () => (
        <PercentField.Root id="offer-probability" locale="en-US" defaultValue={ 4000 } min={ 0 } max={ 10000 }
                           step={ 500 }>
            <PercentField.Label htmlFor="offer-probability">Estimated offer probability</PercentField.Label>
            <PercentField.Group>
                <PercentField.Decrement label="Decrease estimated offer probability"/>
                <PercentField.Input/>
                <PercentField.Increment label="Increase estimated offer probability"/>
            </PercentField.Group>
        </PercentField.Root>
    ),
};

export const Disabled: Story = {
    render: () => (
        <PercentField.Root id="match-rate-disabled" locale="en-US" defaultValue={ 500 } disabled>
            <PercentField.Label htmlFor="match-rate-disabled">401(k) match rate</PercentField.Label>
            <PercentField.Group>
                <PercentField.Decrement label="Decrease match rate"/>
                <PercentField.Input/>
                <PercentField.Increment label="Increase match rate"/>
            </PercentField.Group>
        </PercentField.Root>
    ),
};
