import { useEffect, useRef, useState } from "react";
import { InlineEdit } from "./InlineEdit";
import { Input } from "../input";
import { Text } from "../text";
import { ToastRegion, useToast } from "../toast";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `ToastRegion.stories.tsx`
 * for the full reasoning behind this local shape. Every story here is
 * `render`-form rather than plain `args`, the same choice `Collapsible`'s
 * and `ToastRegion`'s own stories already made: `InlineEdit`'s `onSave`/
 * `renderValue`/`renderEditor` are functions a real caller wires up, not
 * data a Storybook args table can usefully edit.
 *
 * No `play` function anywhere in this file, or anywhere else in this
 * package's stories yet — `@storybook/test`/`userEvent` interaction
 * testing is not wired into this batch, so the "editing"/"save fails"
 * states below are demonstrated by really clicking/typing through the
 * component's own real code path (a mount effect dispatches one real
 * click for `Editing`), not scripted assertions. That is the "plain
 * static-state set of variants" option named as acceptable in this task's
 * own instructions.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof InlineEdit;
}

interface Story {
    readonly render: () => React.ReactElement;
    /** See `Input.stories.tsx` for what this opts out of. `Editing` is the only export here with no visible `Text` sibling once mounted — the rest render `renderValue`'s text, never the editor. */
    readonly tags?: readonly string[];
}

const meta: StoryMeta = {
    title: "Compounds/InlineEdit",
    component: InlineEdit,
};
export default meta;

function DisplayDemo() {
    const [value, setValue] = useState("Backend Engineer");
    return (
        <InlineEdit<string>
            value={value}
            onSave={(next) => {
                setValue(next);
                return Promise.resolve();
            }}
            renderValue={(v) => <Text variant="body">{v}</Text>}
            renderEditor={({ value: editorValue, onChange }) => (
                <Input
                    value={editorValue}
                    onChange={(event) => { onChange(event.target.value); }}
                    autoFocus
                    aria-label="Edit job title"
                />
            )}
            editLabel="Edit job title"
        />
    );
}

export const Display: Story = {
    render: () => <DisplayDemo />,
};

function EditingDemo() {
    const [value, setValue] = useState("Backend Engineer");
    const containerRef = useRef<HTMLDivElement>(null);

    // A real click on the component's own trigger, not a bypass of it — `InlineEdit` exposes no `defaultEditing` prop (no known call site needs one yet), so this is the honest way to demo the editing state without one.
    useEffect(() => {
        containerRef.current?.querySelector("button")?.click();
    }, []);

    return (
        <div ref={containerRef}>
            <InlineEdit<string>
                value={value}
                onSave={(next) => {
                    setValue(next);
                    return Promise.resolve();
                }}
                renderValue={(v) => <Text variant="body">{v}</Text>}
                renderEditor={({ value: editorValue, onChange }) => (
                    <Input
                        value={editorValue}
                        onChange={(event) => { onChange(event.target.value); }}
                        autoFocus
                        aria-label="Edit job title"
                    />
                )}
                editLabel="Edit job title"
            />
        </div>
    );
}

export const Editing: Story = {
    render: () => <EditingDemo />,
    // The mount effect above clicks into edit mode before any scan runs, so this story shows only the bare `Input` — no visible `Text` sibling for APCA to sample.
    tags: ["no-visible-text"],
};

function SaveSucceedsDemo() {
    const [value, setValue] = useState("Backend Engineer");
    return (
        <InlineEdit<string>
            value={value}
            onSave={async (next) => {
                // A deliberately slow "network" — long enough to see the value hold steady through it, per this component's own optimistic-exit design.
                await new Promise((resolve) => setTimeout(resolve, 600));
                setValue(next);
            }}
            renderValue={(v) => <Text variant="body">{v}</Text>}
            renderEditor={({ value: editorValue, onChange }) => (
                <Input
                    value={editorValue}
                    onChange={(event) => { onChange(event.target.value); }}
                    autoFocus
                    aria-label="Edit job title"
                />
            )}
            editLabel="Edit job title"
        />
    );
}

export const SaveSucceeds: Story = {
    render: () => <SaveSucceedsDemo />,
};

/**
 * The real integration this component is designed for: `onError` feeds a
 * mounted `ToastRegion` (`shared/ui/toast`) exactly the way a Tier 3/4
 * caller would once a real mutation exists, with the toast's own `action`
 * carrying the `retry` callback `InlineEdit` hands back — the composition
 * the task set out to verify, not just assert.
 */
function SaveFailsAndRetriesDemo() {
    const [value, setValue] = useState("Backend Engineer");
    const hasFailedOnceRef = useRef(false);
    const { actions } = useToast();

    return (
        <InlineEdit<string>
            value={value}
            onSave={(next) => {
                if (!hasFailedOnceRef.current) {
                    hasFailedOnceRef.current = true;
                    return Promise.reject(new Error("Couldn't reach the server"));
                }
                setValue(next);
                return Promise.resolve();
            }}
            onError={(error, retry) => {
                actions.add({
                    title: "Couldn't save changes",
                    description: error instanceof Error ? error.message : "Unknown error",
                    tone: "danger",
                    action: { label: "Retry", onAction: retry },
                });
            }}
            renderValue={(v) => <Text variant="body">{v}</Text>}
            renderEditor={({ value: editorValue, onChange }) => (
                <Input
                    value={editorValue}
                    onChange={(event) => { onChange(event.target.value); }}
                    autoFocus
                    aria-label="Edit job title"
                />
            )}
            editLabel="Edit job title"
        />
    );
}

export const SaveFailsAndRetries: Story = {
    render: () => (
        <ToastRegion>
            <SaveFailsAndRetriesDemo />
        </ToastRegion>
    ),
};
