import { Link, type LinkProps } from "./Link";
import { Text } from "../text";

/** See `button/Button.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly render: (args: TProps) => React.ReactElement;
    readonly args: TProps;
}

const meta: StoryMeta<LinkProps> = {
    title: "Actions/Link",
    component: Link,
};
export default meta;

/**
 * `Link` composes no `Text` of its own — its own doc comment names it as
 * "inline navigation text," meant to sit inside a real sentence whose
 * surrounding words already declare a typographic purpose, the same way a
 * real call site would never render it as the only thing on the page. Each
 * story wraps it in that realistic body-text context instead, rather than
 * baking a variant into the component that would fight whatever the caller's
 * own paragraph already declares.
 */
export const Default: Story<LinkProps> = {
    args: { href: "/jobs", children: "View jobs" },
    render: (args) => (
        <Text variant="body">
            See the current openings: <Link {...args} />.
        </Text>
    ),
};

export const AsButtonRenderTarget: Story<LinkProps> = {
    args: { href: "/jobs", children: "Inline navigation, styled the same everywhere it appears" },
    render: (args) => (
        <Text variant="body">
            <Link {...args} />
        </Text>
    ),
};
