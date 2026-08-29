import { AppShell } from "frontend-shared/ui/app-shell";
import { Field } from "frontend-shared/ui/field";
import { Input } from "frontend-shared/ui/input";
import { TextArea } from "frontend-shared/ui/text-area";
import { Panel } from "frontend-shared/ui/panel";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { adminNavItems } from "@/app/navigation";

/**
 * Wired to `AppShell` and real form fields on 2026-08-28, replacing the
 * `EmptyState` placeholder. `ThreeColumnLayout` (blocks / fields / live
 * preview, `COMPONENTS.md` §5) and `content-kit`'s `block-editor` widget it
 * would compose are not built yet — see `README.md`'s dated entry for why
 * a single-column form stands in for now rather than a preview of the
 * three-column shape.
 */
export function AdminPageEditorView() {
    return (
        <AppShell navItems={adminNavItems("/pages")} title="Edit page" skipLinkLabel="Skip to content">
            <Panel header={<Text variant="bodyStrong">Page details</Text>}>
                <Stack gap="stack">
                    <Field label="Title">
                        <Input defaultValue="Landing" />
                    </Field>
                    <Field label="Slug" description="The public path this page is served at.">
                        <Input defaultValue="/" />
                    </Field>
                    <Field label="Content" description="Block editing is not built yet — plain text stands in for now.">
                        <TextArea defaultValue="Welcome to Tallyvane." rows={8} />
                    </Field>
                </Stack>
            </Panel>
        </AppShell>
    );
}
