import type { ReactNode } from "react";
import { DataTable, type DataTableColumnDef } from "./DataTable";
import { Badge } from "../badge";
import { Text } from "../text";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — see `Select.stories.tsx` for the
 * full reasoning behind this local shape and its `render`-form stories:
 * `DataTable` is a compound object (`Root`/`Header`/`Body`/`Row`/`Cell`),
 * not a single `component` function `args` can be handed to.
 */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof DataTable.Root;
}

interface Story {
    readonly render: () => React.ReactElement;
}

/**
 * A local, self-contained height — the same `ScrollArea.stories.tsx`
 * exemption for `no-raw-dimension-value` (a literal in a `style` prop, not
 * a named constant): comfortably tall enough to show several rows of the
 * small-dataset stories without needing the real page viewport.
 */
const STORY_VIEWPORT_HEIGHT = "24rem";
/** Taller, so the large-dataset story reads as a real scrollable pipeline table rather than a token-sized demo box. */
const LARGE_STORY_VIEWPORT_HEIGHT = "32rem";

interface Person {
    readonly id: string;
    readonly name: string;
    readonly role: string;
    readonly location: string;
    readonly yearsOfExperience: number;
}

const PEOPLE: Person[] = [
    { id: "1", name: "Ada Lovelace", role: "Engineer", location: "London", yearsOfExperience: 12 },
    { id: "2", name: "Grace Hopper", role: "Engineer", location: "New York", yearsOfExperience: 20 },
    { id: "3", name: "Alan Turing", role: "Researcher", location: "Manchester", yearsOfExperience: 8 },
    { id: "4", name: "Katherine Johnson", role: "Mathematician", location: "Hampton", yearsOfExperience: 15 },
    { id: "5", name: "Margaret Hamilton", role: "Engineer", location: "Cambridge", yearsOfExperience: 18 },
    { id: "6", name: "Radia Perlman", role: "Engineer", location: "Boston", yearsOfExperience: 22 },
];

const PEOPLE_COLUMNS: DataTableColumnDef<Person>[] = [
    { accessorKey: "name", header: "Name" },
    { accessorKey: "role", header: "Role" },
    { accessorKey: "location", header: "Location", enableSorting: false },
    { accessorKey: "yearsOfExperience", header: "Years" },
];

function generatePeople(count: number): Person[] {
    const roles = ["Engineer", "Researcher", "Mathematician", "Designer", "Analyst"];
    const locations = ["London", "New York", "Boston", "Cambridge", "Manchester", "Hampton"];
    return Array.from({ length: count }, (_, index) => ({
        id: String(index),
        name: `Person ${index + 1}`,
        role: roles[index % roles.length] ?? "Engineer",
        location: locations[index % locations.length] ?? "London",
        yearsOfExperience: (index % 30) + 1,
    }));
}
const LARGE_DATASET = generatePeople(1000);

/** `Badge` (Tier 0) rather than plain text — cell content composing an existing token component is the realistic shape of a real column, not a `DataTable` decision. */
function RoleCell({ role }: { readonly role: string }): ReactNode {
    return <Badge tone={role === "Engineer" ? "info" : "neutral"}>{role}</Badge>;
}

const PEOPLE_COLUMNS_WITH_BADGE: DataTableColumnDef<Person>[] = [
    { accessorKey: "name", header: "Name" },
    { accessorKey: "role", header: "Role", cell: (info) => <RoleCell role={info.getValue<string>()} /> },
    { accessorKey: "location", header: "Location", enableSorting: false },
    { accessorKey: "yearsOfExperience", header: "Years" },
];

const meta: StoryMeta = {
    title: "Compounds/DataTable",
    component: DataTable.Root,
};
export default meta;

export const SmallDataset: Story = {
    render: () => (
        <div style={{ height: STORY_VIEWPORT_HEIGHT }}>
            <DataTable.Root data={PEOPLE} columns={PEOPLE_COLUMNS_WITH_BADGE} getRowId={(row) => row.id} aria-label="People">
                <DataTable.Header />
                <DataTable.Body />
            </DataTable.Root>
        </div>
    ),
};

/**
 * `COMPONENTS.md`'s own framing for this component — "the pipeline is a
 * thousand rows at 16 ms a frame" — verified the only way jsdom cannot:
 * scroll this in an actual browser (real `getBoundingClientRect`,
 * `ResizeObserver`) and confirm only a small, constant-sized window of rows
 * is ever mounted regardless of how far the scrollbar travels. A follow-up
 * Playwright spec asserting on scroll position after a keydown — the same
 * hand-written-spec pattern `drawer-keyboard.spec.ts` already establishes —
 * is the next real step for this claim; out of scope for this pass (see
 * this component's own README).
 */
export const LargeVirtualizedDataset: Story = {
    render: () => (
        <div style={{ height: LARGE_STORY_VIEWPORT_HEIGHT }}>
            <DataTable.Root data={LARGE_DATASET} columns={PEOPLE_COLUMNS} getRowId={(row) => row.id} aria-label="1000-row pipeline">
                <DataTable.Header />
                <DataTable.Body />
            </DataTable.Root>
        </div>
    ),
};

/** Click "Name" or "Years" to sort — "Location" has `enableSorting: false` and renders no sort affordance at all, unlike "Role", which is sortable but starts unsorted (`aria-sort="none"`, not omitted). */
export const WithSorting: Story = {
    render: () => (
        <div style={{ height: STORY_VIEWPORT_HEIGHT }}>
            <DataTable.Root data={PEOPLE} columns={PEOPLE_COLUMNS} getRowId={(row) => row.id} aria-label="People, sortable">
                <DataTable.Header />
                <DataTable.Body />
            </DataTable.Root>
        </div>
    ),
};

export const WithRowExpansion: Story = {
    render: () => (
        <div style={{ height: STORY_VIEWPORT_HEIGHT }}>
            <DataTable.Root
                data={PEOPLE}
                columns={PEOPLE_COLUMNS_WITH_BADGE}
                getRowId={(row) => row.id}
                aria-label="People, with notes"
                expandRowLabel="Expand row"
                collapseRowLabel="Collapse row"
                renderExpandedRow={(row) => (
                    <div className="px-inline-tight py-inline-tight">
                        <Text variant="small" color="secondary">
                            {row.name} has {row.yearsOfExperience} years of experience as a {row.role.toLowerCase()} in {row.location}.
                        </Text>
                    </div>
                )}
            >
                <DataTable.Header />
                <DataTable.Body />
            </DataTable.Root>
        </div>
    ),
};
