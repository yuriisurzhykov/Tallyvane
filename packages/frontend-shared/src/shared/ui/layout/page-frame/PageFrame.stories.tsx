import { PageFrame, type PageFrameProps } from "./PageFrame";
import { Text } from "../../text";

/** See `dot/Dot.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<PageFrameProps> = {
    title: "Layout/PageFrame",
    component: PageFrame,
};
export default meta;

export const Default: Story<PageFrameProps> = {
    args: {
        sidebar: <Text variant="body">Brand and navigation</Text>,
        title: "Sign-in policies",
        lead: "What a person must do to register or sign in.",
        skipLinkLabel: "Skip to content",
        children: <Text variant="body">Screen content renders here.</Text>,
    },
};
