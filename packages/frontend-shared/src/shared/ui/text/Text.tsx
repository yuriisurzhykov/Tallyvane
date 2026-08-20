import { useRender } from "@base-ui/react/use-render";
import { mergeProps } from "@base-ui/react/merge-props";

export type TextVariant =
    | "display"
    | "title1"
    | "title2"
    | "title3"
    | "body"
    | "bodyStrong"
    | "small"
    | "caption"
    | "overline"
    | "numeric";

/**
 * `tone` and `color` are mutually exclusive: a status tone already implies a
 * text colour, so allowing both to be set would leave one of them silently
 * ignored. `neutral` is the only tone that leaves `color` free to choose
 * among the three neutral text roles.
 */
type TextColorProps =
    | { readonly tone?: "neutral"; readonly color?: "primary" | "secondary" | "muted" }
    | { readonly tone: "info" | "attention" | "success" | "danger"; readonly color?: never };

export type TextProps = useRender.ComponentProps<"span"> & { readonly variant: TextVariant } & TextColorProps;

const VARIANT_CLASS: Record<TextVariant, string> = {
    display: "text-display",
    title1: "text-title1",
    title2: "text-title2",
    title3: "text-title3",
    body: "text-body",
    bodyStrong: "text-body-strong",
    small: "text-small",
    caption: "text-caption",
    overline: "text-overline",
    numeric: "text-numeric",
};

const COLOR_CLASS: Record<"primary" | "secondary" | "muted", string> = {
    primary: "text-text-primary",
    secondary: "text-text-secondary",
    muted: "text-text-muted",
};

const TONE_CLASS: Record<"info" | "attention" | "success" | "danger", string> = {
    info: "text-status-info-text",
    attention: "text-status-attention-text",
    success: "text-status-success-text",
    danger: "text-status-danger-text",
};

/**
 * Headings (`display`, `title1`–`title3`) default to `<span>`, not a real
 * `<h1>`–`<h6>`: axe's heading-order and one-`<h1>` rules assume a document
 * outline, and this component has no way to know whether a given usage is
 * the page's one true heading or the fifteenth card title in a list. Reusing
 * the variant anywhere else on the page would silently produce a broken
 * outline the moment a real heading tag was the default. A caller who knows
 * a usage genuinely is a heading opts in explicitly via `render={<h1 />}` (or
 * `h2`–`h6`).
 *
 * `body`/`bodyStrong` default to `<p>` — paragraphs carry no such uniqueness
 * constraint, so a real element is the safe and semantically correct
 * default. Every other variant is a short, inline piece of text rather than
 * a paragraph, so it defaults to `<span>`.
 */
function defaultTagFor(variant: TextVariant): "p" | "span" {
    switch (variant) {
        case "body":
        case "bodyStrong":
            return "p";
        case "display":
        case "title1":
        case "title2":
        case "title3":
        case "small":
        case "caption":
        case "overline":
        case "numeric":
            return "span";
        default: {
            const exhaustive: never = variant;
            throw new Error(`Unhandled text variant: ${ String(exhaustive) }`);
        }
    }
}

function resolveColorClassName(tone: TextColorProps["tone"], color: TextColorProps["color"]): string {
    if (tone && tone !== "neutral") return TONE_CLASS[tone];
    return COLOR_CLASS[color ?? "primary"];
}

/** Renders one of the ten text styles on a polymorphic element. The only way type is applied. */
export function Text({ variant, tone, color, render, className, ...props }: TextProps) {
    const classNames = `${ VARIANT_CLASS[variant] } ${ resolveColorClassName(tone, color) }`;

    return useRender({
        defaultTagName: defaultTagFor(variant),
        render,
        props: mergeProps<"span">({ className: classNames }, { ...(className ? { className } : {}), ...props }),
    });
}
