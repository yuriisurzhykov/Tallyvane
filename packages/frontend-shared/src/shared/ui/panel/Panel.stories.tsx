import { Panel, type PanelProps } from "./Panel";
import { Text } from "../text";

/** See `dot/Dot.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<PanelProps> = {
    title: "Surfaces and structure/Panel",
    component: Panel,
};
export default meta;

/**
 * `header`/`children`/`footer` are plain `ReactNode` (`Panel.tsx`'s own
 * "caller decides" typography, same reasoning as `Surface`'s) — each demo
 * string below is wrapped in `Text` here rather than in the component,
 * matching what a real call site would put in these slots.
 */
export const BodyOnly: Story<PanelProps> = {
    args: { children: <Text variant="body">A panel with only a body.</Text> },
};

export const WithHeader: Story<PanelProps> = {
    args: {
        header: <Text variant="bodyStrong">Panel title</Text>,
        children: <Text variant="body">A panel with a header above the body.</Text>,
    },
};

export const WithFooter: Story<PanelProps> = {
    args: {
        children: <Text variant="body">A panel with a footer below the body.</Text>,
        footer: <Text variant="small" color="secondary">Footer actions</Text>,
    },
};

export const WithHeaderAndFooter: Story<PanelProps> = {
    args: {
        header: <Text variant="bodyStrong">Panel title</Text>,
        children: <Text variant="body">A panel with all three slots.</Text>,
        footer: <Text variant="small" color="secondary">Footer actions</Text>,
    },
};

export const Elevated: Story<PanelProps> = {
    args: {
        variant: "elevated",
        header: <Text variant="bodyStrong">Panel title</Text>,
        children: <Text variant="body">An elevated panel.</Text>,
    },
};

export const Inset: Story<PanelProps> = {
    args: {
        variant: "inset",
        header: <Text variant="bodyStrong">Panel title</Text>,
        children: <Text variant="body">An inset panel.</Text>,
    },
};
