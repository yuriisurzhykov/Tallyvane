"use client";

import { Link } from "frontend-shared/ui/link";
import { AppShell } from "frontend-shared/ui/app-shell";
import { Button } from "frontend-shared/ui/button";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useAdminNavItems } from "@/app/navigation";
import type { useAdminAuthenticationStrings } from "@/app/i18n";
import { AuthenticationPolicyEditor, type Action, type Policy, type Scheme, type TokenKind } from "./AuthenticationPolicyEditor";
import { ConfirmationDrawer, ResetFactorPanel } from "./AdminAuthenticationDialogs";
import { defineSettingsSections } from "settings-kit/entities/settings-section";
import { SettingsWorkspace } from "settings-kit/widgets/settings-workspace";

type Translate = ReturnType<typeof useAdminAuthenticationStrings>;
type RemoveConfirmation = Pick<Scheme, "action" | "requiredTokens" | "assuranceRank">;

interface EditorContentProps {
    readonly section: "policy" | "accounts";
    readonly t: Translate;
    readonly tokenLabels: Record<TokenKind, string>;
    readonly actionLabels: Record<Action, string>;
    readonly policy: Policy | null;
    readonly policyDirty: boolean;
    readonly editorResetKey: number;
    readonly conflict: Policy | null;
    readonly loading: boolean;
    readonly busy: boolean;
    readonly error: string | null;
    readonly validationError: string | null;
    readonly unauthorized: boolean;
    readonly email: string;
    readonly confirmation: "advanced" | "reset" | "remove" | null;
    readonly schemeToRemove: RemoveConfirmation | null;
    readonly policyIssue: string | null;
    readonly onReload: () => Promise<void>;
    readonly onUpdate: (id: string, patch: Partial<Scheme>) => void;
    readonly onAdd: (scheme: Omit<Scheme, "id">) => void;
    readonly onRequestRemove: (scheme: Scheme) => void;
    readonly onEmailChange: (email: string) => void;
    readonly onConfirmationChange: (value: "advanced" | "reset" | "remove" | null) => void;
    readonly onResolveConflict: (resolution: "reload" | "replace") => void;
    readonly onCancelDraft: () => void;
    readonly onSave: () => void;
    readonly onReset: () => Promise<void>;
    readonly onConfirm: () => void;
}

export function EditorContent(props: EditorContentProps) {
    const {
        t, tokenLabels, actionLabels, policy, conflict, loading, busy, error, validationError, unauthorized,
        email, confirmation, schemeToRemove, policyIssue,
    } = props;
    const removal = schemeToRemove ? {
        action: actionLabels[schemeToRemove.action],
        path: schemeToRemove.requiredTokens.map(token => tokenLabels[token]).join(" + "),
        rank: schemeToRemove.assuranceRank,
    } : undefined;
    const sections = defineSettingsSections([
        { id: "policy", href: "/authentication", label: t("signInPolicy"), description: t("description") },
        { id: "accounts", href: "/authentication/accounts", label: t("userAccountTools"), description: t("resetHelp") },
    ]);

    return <AppShell
        navItems={ useAdminNavItems("/authentication") }
        navActiveAppearance="subtle"
        title={ t("title") }
        skipLinkLabel={ t("skipLink") }
    >
        <SettingsWorkspace
            sections={sections}
            activeSectionId={props.section}
            headingLevel="h2"
            labels={{ navigation: t("settingsNavigation"), openNavigation: t("openSettingsNavigation"), closeNavigation: t("closeSettingsNavigation") }}
        >
        <Stack gap="stack">
            { error && <Stack role="alert" gap="inline">
                <Text variant="body" tone="danger">{ error }</Text>
                { unauthorized && <Link href="/login?returnTo=/authentication">{ t("signInAdminLink") }</Link> }
                { !conflict && <Button tone="neutral" disabled={ busy || loading } onClick={ () => { void props.onReload(); } }>
                    { t("reloadPolicy") }
                </Button> }
            </Stack> }
            { loading && <Text variant="body" role="status">{ t("loading") }</Text> }
            { props.section === "policy" && !loading && policy && <AuthenticationPolicyEditor
                key={`${String(policy.version)}:${String(props.editorResetKey)}`}
                t={ t }
                tokenLabels={ tokenLabels }
                actionLabels={ actionLabels }
                policy={ policy }
                isDirty={ props.policyDirty }
                onCancelDraft={ props.onCancelDraft }
                busy={ busy }
                policyIssue={ policyIssue }
                validationError={ validationError }
                conflict={ conflict }
                onUpdate={ props.onUpdate }
                onAdd={ props.onAdd }
                onRequestRemove={ props.onRequestRemove }
                onSave={ props.onSave }
                onResolveConflict={ props.onResolveConflict }
            /> }
            { props.section === "accounts" && !loading && policy && <ResetFactorPanel t={ t } email={ email } busy={ busy }
                onEmailChange={ props.onEmailChange }
                onRequest={ () => { props.onConfirmationChange("reset"); }} /> }
        </Stack>
        </SettingsWorkspace>
        <ConfirmationDrawer t={ t } email={ email } confirmation={ confirmation } { ...(removal ? { removal } : {}) }
            onClose={ () => { props.onConfirmationChange(null); }}
            onConfirm={ props.onConfirm }
        />
    </AppShell>;
}
