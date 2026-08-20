import { Dot, type DotProps } from "./Dot";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — importing them here would be a
 * cross-package type import this package cannot resolve, and adding the
 * dependency here is out of scope for this batch. This local shape covers
 * only what a CSF3 story file actually needs: a `{ title, component }`
 * default export and `{ args }` named exports.
 */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<DotProps> = {
    title: "Typography and marks/Dot",
    component: Dot,
};
export default meta;

export const Neutral: Story<DotProps> = { args: { tone: "neutral" } };
export const Info: Story<DotProps> = { args: { tone: "info" } };
export const Attention: Story<DotProps> = { args: { tone: "attention" } };
export const Success: Story<DotProps> = { args: { tone: "success" } };
export const Danger: Story<DotProps> = { args: { tone: "danger" } };

export const WithLabel: Story<DotProps> = { args: { tone: "danger", label: "Overdue" } };
