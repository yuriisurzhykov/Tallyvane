import type { ReactNode } from "react";
import { Avatar as BaseAvatar } from "@base-ui/react/avatar";

export type AvatarSize = "sm" | "md" | "lg";

/**
 * `h-(--control-height-*)`, exactly `IconButton.tsx`'s own `SIZE_CLASS` —
 * reused deliberately rather than introducing a dedicated avatar scale.
 * This is a different question from `Icon`'s own still-open size scale
 * (`COMPONENTS.md` §13's proposed 16/20/24 for the glyph *inside* a
 * control): an avatar is sized like the square/round controls it sits next
 * to in a toolbar or a row, not like the icon glyph a caller might place
 * inside its `Fallback`. See this component's README for the full
 * reasoning and the alternative that was rejected.
 */
const SIZE_CLASS: Record<AvatarSize, string> = {
    sm: "h-(--control-height-sm) w-(--control-height-sm)",
    md: "h-(--control-height-md) w-(--control-height-md)",
    lg: "h-(--control-height-lg) w-(--control-height-lg)",
};

export interface AvatarRootOwnProps {
    /** @default "md" */
    readonly size?: AvatarSize;
    readonly children: ReactNode;
    readonly className?: string;
}

export type AvatarRootProps = AvatarRootOwnProps & Omit<BaseAvatar.Root.Props, "children" | "className">;

/**
 * `rounded-pill` — the same radius role `Dot.tsx` uses for "the other round
 * thing" — is the one shape decision this component makes on its own.
 * `bg-surface-inset` gives `Fallback` a neutral backdrop before an image
 * loads or when none is provided; `overflow-hidden` is what actually keeps
 * `Image` inside the circle once it renders.
 */
const ROOT_CLASS = "inline-flex shrink-0 items-center justify-center overflow-hidden rounded-pill bg-surface-inset";

function Root({ size = "md", children, className, ...rest }: AvatarRootProps) {
    return (
        <BaseAvatar.Root className={ [ROOT_CLASS, SIZE_CLASS[size], className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseAvatar.Root>
    );
}

export interface AvatarImageOwnProps {
    readonly className?: string;
}

export type AvatarImageProps = AvatarImageOwnProps & Omit<BaseAvatar.Image.Props, "className">;

const IMAGE_CLASS = "h-full w-full object-cover";

function Image({ className, ...rest }: AvatarImageProps) {
    return <BaseAvatar.Image className={ [IMAGE_CLASS, className].filter(Boolean).join(" ") } { ...rest } />;
}

export interface AvatarFallbackOwnProps {
    /**
     * Whatever should show in place of a missing or failed image — initials
     * text, a generic person glyph, anything. Deliberately generic: see this
     * component's README for why `Avatar` itself does not compute initials
     * from a name.
     */
    readonly children: ReactNode;
    readonly className?: string;
}

export type AvatarFallbackProps = AvatarFallbackOwnProps & Omit<BaseAvatar.Fallback.Props, "children" | "className">;

const FALLBACK_CLASS = "flex h-full w-full items-center justify-center text-body-strong text-text-secondary select-none";

function Fallback({ children, className, ...rest }: AvatarFallbackProps) {
    return (
        <BaseAvatar.Fallback className={ [FALLBACK_CLASS, className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseAvatar.Fallback>
    );
}

/**
 * Tier 0 — "one of only two round things in the system" (`COMPONENTS.md`'s
 * "Marks and identity" row; `Dot` is the other). Behaviour is Base UI's
 * `@base-ui/react/avatar` (ADR-031) end to end: `Fallback` already renders
 * exactly when there is no image, the image is still loading, or it failed
 * to load (verified against `AvatarFallback.js`'s own `enabled` condition,
 * `imageLoadingStatus !== 'loaded'`), so this wrapper adds no loading-state
 * logic of its own — only tokens, and the compound `Root`/`Image`/
 * `Fallback` surface Base UI already ships.
 */
export const Avatar = { Root, Image, Fallback };
