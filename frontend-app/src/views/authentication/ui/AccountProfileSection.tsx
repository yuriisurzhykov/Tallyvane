"use client";

import { useEffect, useState } from "react";
import { authClient } from "@/features/authentication";
import type { useAuthStrings } from "@/features/authentication/model/strings";
import { Field } from "frontend-shared/ui/field";
import { Button } from "frontend-shared/ui/button";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Text } from "frontend-shared/ui/text";
import { Stack } from "frontend-shared/ui/stack";
import { SettingsFormActions } from "settings-kit/widgets/settings-form";

type Translate = ReturnType<typeof useAuthStrings>;

interface Profile {
    readonly displayName: string | null
}

export function AccountProfileSection({ t }: { readonly t: Translate }) {
    const [saved, setSaved] = useState("");
    const [draft, setDraft] = useState("");
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [loadError, setLoadError] = useState(false);
    const [retry, setRetry] = useState(0);

    useEffect(() => {
        let active = true;
        void authClient.get<Profile>("/account/profile").then(profile => {
            if (!active) return;
            setLoadError(false);
            setSaved(profile.displayName ?? "");
            setDraft(profile.displayName ?? "");
        }).catch(() => {
            if (active) setLoadError(true);
        })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [t, retry]);

    const dirty = draft !== saved;

    async function save() {
        if (!dirty || saving) return;
        setSaving(true);
        setError(null);
        try {
            const profile = await authClient.put<Profile>("/account/profile", { displayName: draft.trim() || null });
            setSaved(profile.displayName ?? "");
            setDraft(profile.displayName ?? "");
        } catch {
            setError(t("settingsSaveFailed"));
        } finally {
            setSaving(false);
        }
    }

    if (loading) return <Text variant="body" role="status">{ t("checkingSession") }</Text>;
    if (loadError) return <Stack gap="inline" role="alert">
        <Text variant="body" tone="danger">{ t("settingsLoadFailed") }</Text>
        <Button type="button" tone="neutral" onClick={ () => {
            setLoading(true);
            setRetry(value => value + 1);
        } }>
            { t("settingsRetry") }
        </Button>
    </Stack>;
    return <Form onSubmit={ event => {
        event.preventDefault();
        void save();
    } }>
        <Field label={ t("settingsDisplayName") } { ...(error ? { error } : {}) }>
            <Input name="displayName"
                   maxLength={ 80 }
                   value={ draft }
                   disabled={ saving }
                   onChange={ event => {
                       setDraft(event.target.value);
                   } }/>
        </Field>
        <SettingsFormActions isDirty={ dirty } isSaving={ saving } onCancel={ () => {
            setDraft(saved);
            setError(null);
        } }
                             labels={ {
                                 save: t("settingsSave"),
                                 cancel: t("settingsCancel"),
                                 saving: t("settingsSaving")
                             } }/>
    </Form>;
}
