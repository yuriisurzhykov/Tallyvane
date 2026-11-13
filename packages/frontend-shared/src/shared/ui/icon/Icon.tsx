import { ArrowUp, Check, ChevronLeft, FolderOpen, Mic, Play, type LucideIcon } from "lucide-react";

export type IconName = "arrowUp" | "check" | "back" | "folder" | "mic" | "play";

const GLYPH: Record<IconName, LucideIcon> = {
    arrowUp: ArrowUp,
    check: Check,
    back: ChevronLeft,
    folder: FolderOpen,
    mic: Mic,
    play: Play,
};

export interface IconProps {
    readonly name: IconName;
    /** Layout only, per `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * Tier 0 — the one place the icon set is named, so the set can be swapped without touching a screen.
 *
 * Always decorative: an icon beside text repeats the text, and an icon alone sits inside a control that
 * already carries an accessible name (`IconButton` requires one). A glyph with a meaning of its own would
 * need its own prop, and nothing needs one yet.
 */
export function Icon({ name, className }: IconProps) {
    const Glyph = GLYPH[name];
    return <Glyph aria-hidden="true" strokeWidth={1.8} className={["size-stack shrink-0", className].filter(Boolean).join(" ")} />;
}
