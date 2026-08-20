import { useState } from "react";
import { SearchField, type SearchFieldProps } from "./SearchField";

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

/**
 * `value`/`onChange` are required, controlled props by this component's own
 * design (see its own report) — a story needs somewhere to hold that state,
 * and this package carries no dependency on Storybook's own `useArgs` to do
 * it the "real" CSF3 way. A small stateful wrapper is the component every
 * story below actually renders, the same way a real call site would own the
 * query state itself.
 */
type ControlledSearchFieldProps = Omit<SearchFieldProps, "value" | "onChange"> & { readonly initialValue?: string };

function ControlledSearchField({ initialValue = "", ...rest }: ControlledSearchFieldProps) {
    const [value, setValue] = useState(initialValue);
    return <SearchField {...rest} value={value} onChange={(event) => setValue(event.target.value)} />;
}

const meta: StoryMeta<ControlledSearchFieldProps> = {
    title: "Inputs/SearchField",
    component: ControlledSearchField,
};
export default meta;

export const Default: Story<ControlledSearchFieldProps> = {
    args: { "aria-label": "Search jobs", onSearch: () => {}, clearLabel: "Clear search" },
};

export const WithValue: Story<ControlledSearchFieldProps> = {
    args: { "aria-label": "Search jobs", initialValue: "staff engineer", onSearch: () => {}, clearLabel: "Clear search" },
};

export const Invalid: Story<ControlledSearchFieldProps> = {
    args: { "aria-label": "Search jobs", "aria-invalid": "true", onSearch: () => {}, clearLabel: "Clear search" },
};

export const Disabled: Story<ControlledSearchFieldProps> = {
    args: { "aria-label": "Search jobs", disabled: true, onSearch: () => {}, clearLabel: "Clear search" },
};
