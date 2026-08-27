import { Accordion } from "./Accordion";
import { Text } from "../text";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Menu.stories.tsx` for the
 * full reasoning behind this local shape and its `render`-form stories:
 * `Accordion` is a compound object (`Root`/`Item`/`Header`/`Trigger`/
 * `Panel`), not a single `component` function `args` can be handed to.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof Accordion.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

/** A placeholder glyph — real icons arrive once `Icon`'s own API is decided (see `COMPONENTS.md` §13), same stand-in `Menu.stories.tsx` already uses. */
function ChevronGlyph({ open }: { readonly open: boolean }) {
    return (
        <svg
            width="16"
            height="16"
            viewBox="0 0 16 16"
            aria-hidden="true"
            className="shrink-0 transition-hover"
            style={ { transform: open ? "rotate(180deg)" : undefined } }
        >
            <path d="M4 6l4 4 4-4" stroke="currentColor" strokeWidth="1.5" fill="none"/>
        </svg>
    );
}

const meta: StoryMeta = {
    title: "Disclosure/Accordion",
    component: Accordion.Root,
};
export default meta;

// `Accordion.Panel` deliberately forces no typography of its own (see its own comment) — each answer's text is wrapped in `Text` here, matching what a real FAQ answer would render.
export const SingleOpen: Story = {
    render: () => (
        <Accordion.Root defaultValue={ ["billing"] }>
            <Accordion.Item value="billing">
                <Accordion.Header>
                    <Accordion.Trigger>How does billing work?</Accordion.Trigger>
                </Accordion.Header>
                <Accordion.Panel><Text variant="body" className="px-stack pb-stack">Monthly, cancel anytime.</Text></Accordion.Panel>
            </Accordion.Item>
            <Accordion.Item value="export">
                <Accordion.Header>
                    <Accordion.Trigger>Can I export my data?</Accordion.Trigger>
                </Accordion.Header>
                <Accordion.Panel><Text variant="body" className="px-stack pb-stack">Yes, as CSV or JSON.</Text></Accordion.Panel>
            </Accordion.Item>
            <Accordion.Item value="support" disabled>
                <Accordion.Header>
                    <Accordion.Trigger>Enterprise support (unavailable)</Accordion.Trigger>
                </Accordion.Header>
                <Accordion.Panel><Text variant="body" className="px-stack pb-stack">Not offered on this plan.</Text></Accordion.Panel>
            </Accordion.Item>
        </Accordion.Root>
    ),
};

export const MultipleOpen: Story = {
    render: () => (
        <Accordion.Root multiple defaultValue={ ["billing", "export"] }>
            <Accordion.Item value="billing">
                <Accordion.Header>
                    <Accordion.Trigger>How does billing work?</Accordion.Trigger>
                </Accordion.Header>
                <Accordion.Panel><Text variant="body" className="px-stack pb-stack">Monthly, cancel anytime.</Text></Accordion.Panel>
            </Accordion.Item>
            <Accordion.Item value="export">
                <Accordion.Header>
                    <Accordion.Trigger>Can I export my data?</Accordion.Trigger>
                </Accordion.Header>
                <Accordion.Panel><Text variant="body" className="px-stack pb-stack">Yes, as CSV or JSON.</Text></Accordion.Panel>
            </Accordion.Item>
        </Accordion.Root>
    ),
};

/** `state.open` from the function-form `render` prop drives the indicator — see `Accordion.tsx`'s own `Trigger` comment for why this replaces a `group-data` attribute selector this codebase does not have a convention for yet. */
export const WithIndicator: Story = {
    render: () => (
        <Accordion.Root defaultValue={ ["billing"] }>
            <Accordion.Item value="billing">
                <Accordion.Header>
                    <Accordion.Trigger
                        render={ (props, state) => (
                            <button { ...props }>
                                <span>How does billing work?</span>
                                <ChevronGlyph open={ state.open }/>
                            </button>
                        ) }
                    />
                </Accordion.Header>
                <Accordion.Panel><Text variant="body" className="px-stack pb-stack">Monthly, cancel anytime.</Text></Accordion.Panel>
            </Accordion.Item>
        </Accordion.Root>
    ),
};
