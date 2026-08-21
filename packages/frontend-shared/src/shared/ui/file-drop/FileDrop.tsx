import type { MouseEvent } from "react";
import { useRef, useState } from "react";
import { FileText, Upload, X } from "lucide-react";
import { VisuallyHidden } from "../visually-hidden";
import { Text } from "../text";
import { Truncate } from "../truncate";
import { Button } from "../button";
import { IconButton } from "../icon-button";
import { CONTROL_ICON_CLASS } from "../../lib";
import { useDragTracking } from "./use-drag-tracking";

export interface FileDropProps {
    /** The primary instruction shown inside the drop zone, e.g. "Drag and drop your résumé here". This component holds no copy of its own (`COMPONENTS.md` §12) — wording and localisation are the caller's. */
    readonly label: string;
    /** Visible text on the "Browse" button, the click-driven alternative to dragging. */
    readonly browseLabel: string;
    /** Accessible name for the button that removes the selected file. */
    readonly clearLabel: string;
    /** Fires with the picked file, or `null` once cleared. See this component's README for why there is no `value`/controlled equivalent — a native file input's selection cannot be set programmatically. */
    readonly onFileChange: (file: File | null) => void;
    /** The native `accept` attribute — only narrows what the Browse dialog offers; a drop is not filtered by it. See the README's "Judgment calls" note. */
    readonly accept?: string;
    readonly disabled?: boolean;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * The drag-over override lives in this same string, keyed off
 * `data-[dragging-over]:`, rather than a second class string conditionally
 * swapped in — an attribute-selector variant always wins over the plain
 * class selectors it overrides regardless of which order Tailwind happens
 * to emit them in its generated stylesheet (a plain class string swap has
 * no such guarantee, since both rules would have equal specificity and the
 * winner would depend on source order in the compiled CSS, not on the
 * order these class names appear in this string). The same reasoning
 * `data-[checked]:`/`data-[disabled]:` already rely on throughout this
 * batch's other components, applied to a genuinely hand-rolled state this
 * one has to track itself.
 */
const CLASS_NAME =
    "flex flex-col items-center justify-center gap-stack-tight rounded-card border-2 border-dashed border-border-default bg-surface-inset p-section-gap text-center transition-hover data-[dragging-over]:border-interactive-primary data-[dragging-over]:bg-interactive-primary-subtle data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

const DROP_ICON_CLASS = `text-text-muted h-[calc(var(--control-icon)*1.5)] w-[calc(var(--control-icon)*1.5)]`;

/**
 * Tier 0 — the drop zone and file selection only, single file. Upload
 * progress and error states are explicitly out of scope (per this batch's
 * brief); an actual upload happens at a higher tier once a real feature
 * needs one, the same "presenter, not the mutation" split
 * `.cursor/skills/component-authoring/SKILL.md` §4 (SRP) draws everywhere
 * else in this ladder.
 *
 * No Base UI primitive backs this (`COMPONENTS.md`'s own `Base` column for
 * this row is `—`) — genuinely justified by the volume/frequency threshold
 * `patterns.md` §7 sets out: one drag-and-drop surface, not the tenth
 * `Menu`/`Combobox`/`Dialog` in this library, so hand-rolling with full
 * rigor (real drag events under test, a real keyboard path, this README)
 * is the legitimate call rather than a shortcut.
 *
 * The keyboard-equivalent path is a real, natively-operable
 * `<input type="file">`, visually hidden via this package's own
 * `VisuallyHidden` (Tier 0 composing Tier 0 — carries no domain knowledge,
 * `COMPONENTS.md` §2) rather than `display: none`, so Tab still reaches it
 * and native Enter/Space activation opens the OS file dialog with no JS of
 * this component's own needed for that part. The visible "Browse" button
 * is a second, independently focusable way to reach the same dialog — both
 * exist because drag-and-drop alone has no keyboard equivalent at all
 * (WCAG 2.1.1). The dashed drop-zone `<div>` itself is deliberately NOT
 * part of the tab order: it is a mouse/pointer convenience (clicking
 * anywhere in it also opens the dialog, via the same handler the hidden
 * input's own click bubbles through), not a third redundant tab stop for
 * an already keyboard-reachable action.
 *
 * Drag-state tracking lives in `useDragTracking` (colocated in this same
 * directory) — see that file for why it's a counter, not a boolean.
 */
export function FileDrop({
                             label,
                             browseLabel,
                             clearLabel,
                             onFileChange,
                             accept,
                             disabled = false,
                             className
                         }: FileDropProps) {
    const inputRef = useRef<HTMLInputElement>(null);
    const [selectedFile, setSelectedFile] = useState<File | null>(null);

    function openFileDialog() {
        if (!disabled) {
            inputRef.current?.click();
        }
    }

    function selectFile(file: File | null) {
        setSelectedFile(file);
        onFileChange(file);
    }

    function handleClear(event: MouseEvent) {
        // The clear button sits inside the drop zone `<div>`, whose own
        // `onClick` re-opens the file dialog — without this, clearing a
        // file would immediately pop the picker back up.
        event.stopPropagation();
        selectFile(null);
        if (inputRef.current) {
            inputRef.current.value = "";
        }
    }

    const { isDraggingOver, handleDragEnter, handleDragOver, handleDragLeave, handleDrop } = useDragTracking(
        disabled,
        (file) => {
            if (file) selectFile(file);
        },
    );

    return (
        <div
            className={ [CLASS_NAME, className].filter(Boolean).join(" ") }
            { ...(disabled ? { "data-disabled": "", "aria-disabled": "true" as const } : {}) }
            { ...(isDraggingOver ? { "data-dragging-over": "" } : {}) }
            onClick={ openFileDialog }
            onDragEnter={ handleDragEnter }
            onDragOver={ handleDragOver }
            onDragLeave={ handleDragLeave }
            onDrop={ handleDrop }
        >
            <VisuallyHidden
                render={
                    <input
                        ref={ inputRef }
                        type="file"
                        aria-label={ browseLabel }
                        { ...(accept ? { accept } : {}) }
                        disabled={ disabled }
                        onChange={ (event) => { selectFile(event.target.files?.[0] ?? null); } }
                        // `HTMLInputElement.click()` dispatches a real, bubbling
                        // click event — without this, opening the dialog via
                        // `openFileDialog`'s own `inputRef.current.click()` call
                        // would re-trigger the drop zone's own `onClick` a second
                        // time (input → bubbles up through the zone that
                        // contains it → calls `openFileDialog` again), the exact
                        // "implementation detail must not reach ancestors"
                        // problem Base UI's own hidden inputs guard against the
                        // same way (`CheckboxRoot.js`'s `inputProps.onClick`).
                        // Found empirically: a first draft of `FileDrop.test.tsx`
                        // recorded the input's `click()` firing twice per real
                        // click, not once.
                        onClick={ (event) => { event.stopPropagation(); } }
                    />
                }
            />
            { selectedFile ? (
                <SelectedFileView file={ selectedFile } clearLabel={ clearLabel } disabled={ disabled } onClear={ handleClear } />
            ) : (
                <BrowsePrompt label={ label } browseLabel={ browseLabel } disabled={ disabled } onBrowse={ openFileDialog } />
            ) }
        </div>
    );
}

function SelectedFileView({ file, clearLabel, disabled, onClear }: {
    readonly file: File;
    readonly clearLabel: string;
    readonly disabled: boolean;
    readonly onClear: (event: MouseEvent) => void;
}) {
    return (
        <>
            <FileText aria-hidden="true" className={DROP_ICON_CLASS}/>
            <div className="flex items-center gap-inline-tight">
                {/* `Truncate` takes no `style` prop (see its own README) — the width cap lives on this plain wrapper instead, which `Truncate`'s own `overflow-hidden` then clips against. */ }
                <div style={ { maxWidth: "var(--ds-component-file-drop-filename-max-width)" } }>
                    <Truncate fullValue={ file.name }>
                        <Text variant="small" color="primary">
                            { file.name }
                        </Text>
                    </Truncate>
                </div>
                <IconButton label={ clearLabel } tone="ghost" size="sm" disabled={ disabled } onClick={ onClear }>
                    <X aria-hidden="true" className={CONTROL_ICON_CLASS}/>
                </IconButton>
            </div>
        </>
    );
}

function BrowsePrompt({ label, browseLabel, disabled, onBrowse }: {
    readonly label: string;
    readonly browseLabel: string;
    readonly disabled: boolean;
    readonly onBrowse: () => void;
}) {
    return (
        <>
            <Upload aria-hidden="true" className={DROP_ICON_CLASS}/>
            <Text variant="small" color="muted">
                { label }
            </Text>
            <Button
                tone="neutral"
                size="sm"
                disabled={ disabled }
                onClick={ (event) => {
                    // The button sits inside the drop zone `<div>`, whose own
                    // `onClick` also calls `openFileDialog` — without this, one
                    // Browse click would call it twice (once here, once more
                    // from bubbling to the zone).
                    event.stopPropagation();
                    onBrowse();
                } }
            >
                { browseLabel }
            </Button>
        </>
    );
}
