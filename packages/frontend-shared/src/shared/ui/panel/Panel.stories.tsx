import { Panel, type PanelProps } from "./Panel";

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

export const BodyOnly: Story<PanelProps> = {
    args: { children: "A panel with only a body." },
};

export const WithHeader: Story<PanelProps> = {
    args: { header: "Panel title", children: "A panel with a header above the body." },
};

export const WithFooter: Story<PanelProps> = {
    args: { children: "A panel with a footer below the body.", footer: "Footer actions" },
};

export const WithHeaderAndFooter: Story<PanelProps> = {
    args: { header: "Panel title", children: "A panel with all three slots.", footer: "Footer actions" },
};

export const Elevated: Story<PanelProps> = {
    args: { variant: "elevated", header: "Panel title", children: "An elevated panel." },
};

export const Inset: Story<PanelProps> = {
    args: { variant: "inset", header: "Panel title", children: "An inset panel." },
};
