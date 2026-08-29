import { TopBar, type TopBarProps } from "./TopBar";
import { IconButton } from "../../icon-button";

/** See `dot/Dot.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<TopBarProps> = {
    title: "Layout/TopBar",
    component: TopBar,
};
export default meta;

export const TitleOnly: Story<TopBarProps> = {
    args: { title: "Today" },
};

export const WithActions: Story<TopBarProps> = {
    args: {
        title: "Pages",
        actions: (
            <IconButton label="Refresh" tone="ghost">
                ⟳
            </IconButton>
        ),
    },
};
