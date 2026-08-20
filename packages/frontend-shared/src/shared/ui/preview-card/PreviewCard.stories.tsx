import { PreviewCard } from "./PreviewCard";
import { Text } from "../text";

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
            <Text variant="body" className="text-interactive-primary-text underline" render={<PreviewCard.Trigger href="/jobs/123" />}>
                Senior Platform Engineer at Acme Corp
            </Text>
            <PreviewCard.Popup>
                <Text variant="body">Senior Platform Engineer</Text>
                <Text variant="body">Acme Corp — Remote — $180k–$210k</Text>
            </PreviewCard.Popup>
        </PreviewCard.Root>
    ),
};

export const WithArrow: Story = {
    render: () => (
        <PreviewCard.Root>
            <Text variant="body" className="text-interactive-primary-text underline" render={<PreviewCard.Trigger href="/contacts/456" />}>
                Jordan Blake
            </Text>
            <PreviewCard.Popup side="bottom">
                <PreviewCard.Arrow />
                <Text variant="body">Jordan Blake</Text>
                <Text variant="body">Engineering Manager — met at conference</Text>
            </PreviewCard.Popup>
        </PreviewCard.Root>
    ),
};
