import { Badge, type BadgeProps } from "./Badge";

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

const meta: StoryMeta<BadgeProps> = {
    title: "Status and feedback/Badge",
    component: Badge,
};
export default meta;

export const Neutral: Story<BadgeProps> = { args: { tone: "neutral", children: "Draft" } };
export const Info: Story<BadgeProps> = { args: { tone: "info", children: "New" } };
export const Attention: Story<BadgeProps> = { args: { tone: "attention", children: "Needs review" } };
export const Success: Story<BadgeProps> = { args: { tone: "success", children: "Hired" } };
export const Danger: Story<BadgeProps> = { args: { tone: "danger", children: "Rejected" } };

export const SolidNeutral: Story<BadgeProps> = { args: { tone: "neutral", treatment: "solid", children: "Draft" } };
export const SolidInfo: Story<BadgeProps> = { args: { tone: "info", treatment: "solid", children: "New" } };
export const SolidAttention: Story<BadgeProps> = {
    args: {
        tone: "attention",
        treatment: "solid",
        children: "Needs review"
    }
};
export const SolidSuccess: Story<BadgeProps> = { args: { tone: "success", treatment: "solid", children: "Hired" } };
export const SolidDanger: Story<BadgeProps> = { args: { tone: "danger", treatment: "solid", children: "Rejected" } };
