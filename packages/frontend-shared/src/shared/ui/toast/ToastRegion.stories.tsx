import { Button } from "../button";
import { ToastRegion, useToast } from "./ToastRegion";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Button.stories.tsx` for the
 * full reasoning behind this local shape.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof ToastRegion;
}

interface Story {
    readonly render: () => React.ReactElement;
}

const meta: StoryMeta = {
    title: "Overlays/ToastRegion",
    component: ToastRegion,
};
export default meta;

function FailureDemo() {
    const { actions } = useToast();
    return (
        <Button tone="danger" onClick={() => actions.add({ title: "Couldn't save changes", description: "Check your connection and try again.", tone: "danger" })}>
            Simulate a failed save
        </Button>
    );
}

export const Default: Story = {
    render: () => (
        <ToastRegion>
            <FailureDemo />
        </ToastRegion>
    ),
};

function UndoDemo() {
    const { actions } = useToast();
    return (
        <Button
            tone="neutral"
            onClick={() =>
                actions.add({
                    title: "Application archived",
                    tone: "neutral",
                    action: { label: "Undo", onAction: () => actions.add({ title: "Restored", tone: "success", timeout: 2000 }) },
                })
            }
        >
            Archive application
        </Button>
    );
}

export const WithUndo: Story = {
    render: () => (
        <ToastRegion>
            <UndoDemo />
        </ToastRegion>
    ),
};

function AllTonesDemo() {
    const { actions } = useToast();
    return (
        <div className="flex gap-inline-tight">
            <Button tone="neutral" onClick={() => actions.add({ title: "Draft saved", tone: "neutral" })}>
                Neutral
            </Button>
            <Button tone="neutral" onClick={() => actions.add({ title: "New reply", tone: "info" })}>
                Info
            </Button>
            <Button tone="neutral" onClick={() => actions.add({ title: "Application submitted", tone: "success" })}>
                Success
            </Button>
            <Button tone="neutral" onClick={() => actions.add({ title: "Interview in 10 minutes", tone: "attention" })}>
                Attention
            </Button>
            <Button tone="danger" onClick={() => actions.add({ title: "Couldn't save changes", tone: "danger" })}>
                Danger
            </Button>
        </div>
    );
}

export const AllTones: Story = {
    render: () => (
        <ToastRegion>
            <AllTonesDemo />
        </ToastRegion>
    ),
};
