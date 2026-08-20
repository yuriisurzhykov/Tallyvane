import { Numeric, type NumericProps } from "./Numeric";

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

const meta: StoryMeta<NumericProps> = {
    title: "Typography and marks/Numeric",
    component: Numeric,
};
export default meta;

export const RightAligned: Story<NumericProps> = { args: { children: "$185,000" } };
export const LeftAligned: Story<NumericProps> = { args: { align: "left", children: "$185,000" } };
export const SlashedZero: Story<NumericProps> = { args: { children: "10,203,040" } };
