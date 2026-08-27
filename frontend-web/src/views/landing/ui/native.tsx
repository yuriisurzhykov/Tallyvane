import { createElement, type HTMLAttributes, type ReactElement, type ReactNode } from "react";

/**
 * Views cannot write a lowercase JSX tag (`<h1 />`, `<main>`) — eslint
 * `no-restricted-syntax` reserves those for `packages/frontend-shared/src/shared/ui`.
 * Text and Accordion still need a real heading element via their `render` prop,
 * and the page still needs header/main/footer landmarks.
 *
 * `render` is the function form, not a pre-built element: a static
 * `createElement("h1")` was measured at 16px/400 because Base UI never merged
 * the variant class onto it. The callback receives the merged props (class,
 * children, aria) and puts them on the tag. A future AppShell is the place
 * header/main/footer should move to, not a reason to invent heading primitives
 * in shared today.
 */
export function nativeRender(tag: "h1" | "h2") {
    return (props: HTMLAttributes<HTMLElement>) => createElement(tag, props);
}

export function Native({
    as,
    children,
    className,
}: {
    readonly as: "header" | "main" | "footer";
    readonly children: ReactNode;
    readonly className?: string;
}): ReactElement {
    return createElement(as, { className }, children);
}
