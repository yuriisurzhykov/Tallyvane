# settings-kit

`settings-kit` is the shared settings interface used by an application and the
admin console. It provides section navigation, groups and rows, immediate
switches, explicit form actions, and protection for unfinished changes.

Each application owns its section names, route table, data loading, API calls,
validation, and multi-step workflows. The package ships no default sections or
settings resources.

## Composition

Declare the section metadata beside the application's route composition. Keep
the route as the source of the active section and render its module into the
workspace slot:

```tsx
import { defineSettingsSections } from "settings-kit/entities/settings-section";
import {
    SettingsNavigationGuardProvider,
} from "settings-kit/features/settings-navigation";
import { SettingsWorkspace } from "settings-kit/widgets/settings-workspace";

const sections = defineSettingsSections([
    { id: "preferences", href: "/settings/preferences", label: "Preferences" },
    { id: "access", href: "/settings/access", label: "Access", description: "Sign-in and access controls." },
]);

function SettingsRoute({ activeSectionId, children, router }) {
    return (
        <SettingsNavigationGuardProvider
            labels={{
                title: "Unsaved changes",
                description: "Your changes have not been saved.",
                stay: "Stay",
                leave: "Leave without saving",
            }}
            navigate={(href) => router.push(href)}
        >
            <SettingsWorkspace
                sections={sections}
                activeSectionId={activeSectionId}
                labels={{ navigation: "Settings", openNavigation: "Sections", closeNavigation: "Close" }}
            >
                {children}
            </SettingsWorkspace>
        </SettingsNavigationGuardProvider>
    );
}
```

The `view` prop can replace the standard desktop rail/mobile left drawer for a
single workspace. A module can call `useSettingsNavigationGuard()` to route its
own links through the same Stay/Leave confirmation, and
`useSettingsUnsavedChanges()` to register unfinished multi-step progress.
The provider also enables the browser's native warning on tab close or reload.

## Building a section

Compose the public design-system components around module-owned fields and
mutations. `SettingsToggle` owns optimistic state, ordered saves, rollback, and
retry feedback; `SettingsFormActions` supplies Save/Cancel actions and
registers the form's dirty state. The field values and validation remain with
the module.

```tsx
import { SettingsToggle } from "settings-kit/features/settings-toggle";
import { SettingsFormActions } from "settings-kit/widgets/settings-form";
import { SettingsGroup } from "settings-kit/widgets/settings-group";
import { Form } from "frontend-shared/ui/form";

function PreferencesSection() {
    return (
        <SettingsGroup title="Availability" description="Choose which updates you receive.">
            <SettingsToggle
                label="Product updates"
                value={enabled}
                onSave={saveEnabled}
                statusLabels={{ saving: "Saving", saved: "Saved", error: "Could not save", retry: "Retry" }}
            />
            <Form onFormSubmit={saveProfileDraft}>
                { /* Build domain fields with frontend-shared Field, Input, and Select. */ }
                <SettingsFormActions
                    isDirty={isDirty}
                    isSaving={isSaving}
                    onCancel={resetDraft}
                    labels={{ save: "Save", cancel: "Cancel", saving: "Saving" }}
                />
            </Form>
        </SettingsGroup>
    );
}
```

`SettingsItem` is available when a module needs a different control or action
beside the same label/description row. All exported UI is assembled from
public `frontend-shared` components.
