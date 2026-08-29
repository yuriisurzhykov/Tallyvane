import { AppShell } from "frontend-shared/ui/app-shell";
import { KeyValueList } from "frontend-shared/ui/key-value-list";
import { Panel } from "frontend-shared/ui/panel";
import { Text } from "frontend-shared/ui/text";
import { adminNavItems } from "@/app/navigation";

/**
 * Static for now — no strings API exists yet to read a real namespace from.
 * See `README.md`'s dated entry.
 */
const MOCK_STRINGS = [
    { label: "landing.tagline", value: "Run your job search like a system, not a spreadsheet." },
    { label: "landing.footerTagline", value: "Built by one engineer, for one engineer." },
    { label: "common.productName", value: "Tallyvane" },
];

/**
 * Wired to `AppShell` and a real `KeyValueList` on 2026-08-28, replacing the
 * `EmptyState` placeholder. Editing (not just reading), namespace grouping
 * and the default-value comparison ARCHITECTURE.md §13.3 describes are not
 * built yet — see `README.md`'s dated entry.
 */
export function AdminStringsView() {
    return (
        <AppShell navItems={adminNavItems("/strings")} title="Strings" skipLinkLabel="Skip to content">
            <Panel header={<Text variant="bodyStrong">common</Text>}>
                <KeyValueList
                    items={MOCK_STRINGS.map((entry) => ({
                        label: entry.label,
                        value: (
                            <Text variant="small" color="primary">
                                {entry.value}
                            </Text>
                        ),
                    }))}
                />
            </Panel>
        </AppShell>
    );
}
