import { Truncate, type TruncateProps } from "./Truncate";

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

const meta: StoryMeta<TruncateProps> = {
    title: "Typography and marks/Truncate",
    component: Truncate,
};
export default meta;

const LONG_VALUE =
    "A very long job title that would otherwise wrap onto several lines and break a dense table row's height";

export const SingleLine: Story<TruncateProps> = {
    args: { fullValue: LONG_VALUE, children: LONG_VALUE },
};

export const ThreeLines: Story<TruncateProps> = {
    args: { lines: 3, fullValue: "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.", children: LONG_VALUE },
};

export const WithoutFullValue: Story<TruncateProps> = {
    args: { children: LONG_VALUE },
};
