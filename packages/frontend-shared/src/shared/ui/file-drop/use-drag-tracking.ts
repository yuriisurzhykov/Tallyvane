import type { DragEvent } from "react";
import { useRef, useState } from "react";

/**
 * Tracks whether the pointer is currently dragging a file over the zone,
 * and reports a completed drop. Extracted out of `FileDrop` itself because
 * this piece is a genuinely separate concern from file selection or
 * rendering — the drag depth counter and the four drag event handlers below
 * are the only things that touch it.
 *
 * A counter, not a boolean flipped on every `dragenter`/`dragleave`: both
 * events fire again for every child element the pointer crosses while still
 * inside the drop zone (the icon, the instruction text, the Browse button),
 * so a naive boolean flickers the active-drag styling off the moment the
 * pointer enters any of them. The counter only reaches zero, and only then
 * clears the active styling, once the pointer has actually left every
 * nested element — verified in `FileDrop.test.tsx` by dispatching a
 * `dragenter`/`dragleave` pair on a child before the real `drop`, not
 * assumed from reading this comment alone.
 */
export function useDragTracking(disabled: boolean, onDrop: (file: File | null) => void) {
    const dragDepthRef = useRef(0);
    const [isDraggingOver, setIsDraggingOver] = useState(false);

    function handleDragEnter(event: DragEvent<HTMLDivElement>) {
        if (disabled) return;
        event.preventDefault();
        dragDepthRef.current += 1;
        setIsDraggingOver(true);
    }

    function handleDragOver(event: DragEvent<HTMLDivElement>) {
        if (disabled) return;
        // Required for `drop` to fire at all — a `dragover` with no handler
        // (or one that doesn't prevent the default) tells the browser this
        // is not a valid drop target.
        event.preventDefault();
    }

    function handleDragLeave(event: DragEvent<HTMLDivElement>) {
        if (disabled) return;
        event.preventDefault();
        dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
        if (dragDepthRef.current === 0) {
            setIsDraggingOver(false);
        }
    }

    function handleDrop(event: DragEvent<HTMLDivElement>) {
        if (disabled) return;
        event.preventDefault();
        dragDepthRef.current = 0;
        setIsDraggingOver(false);
        onDrop(event.dataTransfer.files[0] ?? null);
    }

    return { isDraggingOver, handleDragEnter, handleDragOver, handleDragLeave, handleDrop };
}
