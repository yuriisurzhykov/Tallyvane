import { NumberField } from "./NumberField";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning behind this local shape. `NumberField` has no single
 * `component` function to hand `args` to — it is a compound object assembled
 * differently per story, the same shape `Menu.stories.tsx`/`Popover.stories.tsx`
 * already use for their own compound overlays, so every story below uses
 * CSF3's `render` form instead of `args`.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof NumberField.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Inputs/NumberField",
    component: NumberField.Root,
};
export default meta;

export const Default: Story = {
    render: () => (
        <NumberField.Root id="years-experience" defaultValue={ 3 } min={ 0 } max={ 40 }>
            <NumberField.Label htmlFor="years-experience">Years of experience</NumberField.Label>
            <NumberField.Group>
                <NumberField.Decrement label="Decrease years of experience"/>
                <NumberField.Input/>
                <NumberField.Increment label="Increase years of experience"/>
            </NumberField.Group>
        </NumberField.Root>
    ),
};

export const Sizes: Story = {
    render: () => (
        <div className="flex flex-col items-start gap-stack">
            { (["sm", "md", "lg"] as const).map((size) => (
                <NumberField.Root key={ size } id={ `rounds-${ size }` } defaultValue={ 2 } min={ 0 }>
                    <NumberField.Label htmlFor={ `rounds-${ size }` }>{ `Interview rounds (${ size })` }</NumberField.Label>
                    <NumberField.Group size={ size }>
                        <NumberField.Decrement label="Decrease interview rounds" size={ size }/>
                        <NumberField.Input/>
                        <NumberField.Increment label="Increase interview rounds" size={ size }/>
                    </NumberField.Group>
                </NumberField.Root>
            )) }
        </div>
    ),
};

export const WithMinMax: Story = {
    render: () => (
        <NumberField.Root id="fit-rating" defaultValue={ 3 } min={ 1 } max={ 5 }>
            <NumberField.Label htmlFor="fit-rating">Fit rating (1–5)</NumberField.Label>
            <NumberField.Group>
                <NumberField.Decrement label="Decrease fit rating"/>
                <NumberField.Input/>
                <NumberField.Increment label="Increase fit rating"/>
            </NumberField.Group>
        </NumberField.Root>
    ),
};

export const Disabled: Story = {
    render: () => (
        <NumberField.Root id="headcount-disabled" defaultValue={ 5 } disabled>
            <NumberField.Label htmlFor="headcount-disabled">Headcount</NumberField.Label>
            <NumberField.Group>
                <NumberField.Decrement label="Decrease headcount"/>
                <NumberField.Input/>
                <NumberField.Increment label="Increase headcount"/>
            </NumberField.Group>
        </NumberField.Root>
    ),
};

export const WithScrubArea: Story = {
    render: () => (
        <NumberField.Root id="scrub-amount" defaultValue={ 100 }>
            <NumberField.ScrubArea>
                <NumberField.Label htmlFor="scrub-amount">Amount (drag the label to scrub)</NumberField.Label>
            </NumberField.ScrubArea>
            <NumberField.Group>
                <NumberField.Decrement label="Decrease amount"/>
                <NumberField.Input/>
                <NumberField.Increment label="Increase amount"/>
            </NumberField.Group>
        </NumberField.Root>
    ),
};
