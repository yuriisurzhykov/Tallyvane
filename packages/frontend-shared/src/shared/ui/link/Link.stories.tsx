import { Link, type LinkProps } from "./Link";

/** See `button/Button.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<LinkProps> = {
    title: "Actions/Link",
    component: Link,
};
export default meta;

export const Default: Story<LinkProps> = { args: { href: "/jobs", children: "View jobs" } };

export const AsButtonRenderTarget: Story<LinkProps> = {
    args: { href: "/jobs", children: "Inline navigation, styled the same everywhere it appears" },
};
