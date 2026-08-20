import { Callout } from "../callout";
import { LiveRegion } from "./LiveRegion";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning behind this local shape. Every story below uses CSF3's
 * `render` form, not `args`: `LiveRegion` itself is correctly, deliberately
 * invisible, so each story also renders a visible preview alongside it (see
 * `LiveRegionDemo`'s own comment) — a plain `args` pass-through has nowhere
 * to attach that preview.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof LiveRegion;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Status and feedback/LiveRegion",
    component: LiveRegion,
};
export default meta;

/**
 * `LiveRegion` itself is correctly, deliberately invisible — that is its
 * entire job, not a bug this story works around. What was missing was a way
 * for a *sighted* developer looking at this story's canvas to tell "nothing
 * rendered" apart from "working exactly as designed": both look identical
 * on screen. Each story therefore also renders a visible preview of the
 * same announcement text next to a real, still-genuinely-hidden
 * `LiveRegion` — the preview is not what a screen reader announces (that is
 * the actual hidden region below it), it exists purely so this page is not
 * a blank rectangle. Verifying the real announcement still needs a screen
 * reader or an accessibility tree inspector, which no visual story can
 * substitute for.
 */
function LiveRegionDemo({ politeness, message }: {
    politeness: "polite" | "assertive";
    message: string
}): React.ReactElement {
    return (
        <div className="flex flex-col gap-stack">
            <Callout tone="info">
                <strong>Not what you see on screen.</strong> This is a sighted preview only — the
                real <code>LiveRegion</code> below announces this same text to screen readers via{ " " }
                <code>aria-live=&quot;{ politeness }&quot;</code> while staying visually hidden, exactly
                as designed. Inspect the accessibility tree, or use a screen reader, to verify the
                real announcement.
            </Callout>
            <LiveRegion politeness={ politeness }>{ message }</LiveRegion>
        </div>
    );
}

export const Polite: Story = {
    render: () => <LiveRegionDemo politeness="polite" message="12 jobs matched your filters"/>,
};
export const Assertive: Story = {
    render: () => <LiveRegionDemo politeness="assertive" message="Autosave failed — changes were not saved"/>,
};
