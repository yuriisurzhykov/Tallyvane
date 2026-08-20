import { PreviewCard } from "./PreviewCard";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning behind this local shape.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof PreviewCard.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Overlays/PreviewCard",
    component: PreviewCard.Root,
};
export default meta;

export const Default: Story = {
    render: () => (
        <PreviewCard.Root>
            <PreviewCard.Trigger href="/jobs/123" className="text-interactive-primary-text underline">
                Senior Platform Engineer at Acme Corp
            </PreviewCard.Trigger>
            <PreviewCard.Popup>
                <p>Senior Platform Engineer</p>
                <p>Acme Corp — Remote — $180k–$210k</p>
            </PreviewCard.Popup>
        </PreviewCard.Root>
    ),
};

export const WithArrow: Story = {
    render: () => (
        <PreviewCard.Root>
            <PreviewCard.Trigger href="/contacts/456" className="text-interactive-primary-text underline">
                Jordan Blake
            </PreviewCard.Trigger>
            <PreviewCard.Popup side="bottom">
                <PreviewCard.Arrow />
                <p>Jordan Blake</p>
                <p>Engineering Manager — met at conference</p>
            </PreviewCard.Popup>
        </PreviewCard.Root>
    ),
};
