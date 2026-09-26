import type { ComponentPropsWithoutRef } from "react";

export type ImageFit = "contain" | "cover";

export type ImageProps = Omit<
    ComponentPropsWithoutRef<"img">,
    "alt" | "className" | "decoding" | "draggable" | "loading" | "src"
> & {
    readonly src: string;
    readonly alt: string;
    readonly fit?: ImageFit;
    readonly loading?: "eager" | "lazy";
    readonly className?: string;
};

const FIT_CLASS: Record<ImageFit, string> = {
    contain: "object-contain",
    cover: "object-cover",
};

/** Accessible media with non-blocking decode, lazy loading, and an explicit fit policy. */
export function Image({ src, alt, fit = "contain", loading = "lazy", className, ...props }: ImageProps) {
    return (
        <img
            src={src}
            alt={alt}
            loading={loading}
            decoding="async"
            draggable={false}
            className={["block max-w-full", FIT_CLASS[fit], className].filter(Boolean).join(" ")}
            {...props}
        />
    );
}
