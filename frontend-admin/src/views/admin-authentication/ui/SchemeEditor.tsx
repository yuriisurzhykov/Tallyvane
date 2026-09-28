"use client";

import { Button } from "frontend-shared/ui/button";
import { Checkbox } from "frontend-shared/ui/checkbox";
import { Collapsible } from "frontend-shared/ui/collapsible";
import { Field } from "frontend-shared/ui/field";
import { Fieldset } from "frontend-shared/ui/fieldset";
import { Input } from "frontend-shared/ui/input";
import { Panel } from "frontend-shared/ui/panel";
import { Row } from "frontend-shared/ui/row";
import { Select } from "frontend-shared/ui/select";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { Scheme, SchemeDraft, TokenKind, TokenLabels, Translate } from "./AuthenticationPolicyEditor";

const tokenKinds: TokenKind[] = ["PASSWORD", "GOOGLE", "EMAIL_SIGN_IN_CODE", "TOTP", "EMAIL_FACTOR_CODE"];
const primaryTokens: TokenKind[] = ["PASSWORD", "GOOGLE", "EMAIL_SIGN_IN_CODE"];
const factorTokens: TokenKind[] = ["TOTP", "EMAIL_FACTOR_CODE"];

interface SchemeEditorProps {
    readonly t: Translate;
    readonly actionLabel: string;
    readonly tokenLabels: TokenLabels;
    readonly scheme: SchemeDraft;
    readonly editing: boolean;
    readonly busy: boolean;
    readonly validationError: string | null;
    readonly onChange: (patch: Partial<SchemeDraft>) => void;
    readonly onCancel: () => void;
    readonly onSave: () => void;
}

interface SchemeProofFieldsProps {
    readonly t: Translate;
    readonly tokenLabels: TokenLabels;
    readonly isSignIn: boolean;
    readonly primary: TokenKind;
    readonly factor: TokenKind | undefined;
    readonly extraTokens: TokenKind[];
    readonly busy: boolean;
    readonly setPrimary: (value: string | null) => void;
    readonly setFactor: (value: string | null) => void;
    readonly toggleExtraToken: (token: TokenKind, checked: boolean) => void;
}

function SchemeProofFields({
    t, tokenLabels, isSignIn, primary, factor, extraTokens, busy, setPrimary, setFactor, toggleExtraToken,
}: SchemeProofFieldsProps) {
    return <Stack gap="stack">
        <Field label={ t(isSignIn ? "primaryMethod" : "startingProof") }>
            <Select.Root value={ primary } disabled={ busy } onValueChange={ setPrimary }>
                <Select.Trigger><Select.Value>{ tokenLabels[primary] }</Select.Value><Select.Icon /></Select.Trigger>
                <Select.Popup>
                    { (isSignIn ? primaryTokens : tokenKinds).map(token => <Select.Item key={ token } value={ token }>
                        { tokenLabels[token] }
                    </Select.Item>) }
                </Select.Popup>
            </Select.Root>
        </Field>
        { isSignIn
            ? <Field label={ t("additionalFactorOptional") }>
                <Select.Root value={ factor ?? "none" } disabled={ busy } onValueChange={ setFactor }>
                    <Select.Trigger><Select.Value>{ factor ? tokenLabels[factor] : t("noAdditionalProof") }</Select.Value><Select.Icon /></Select.Trigger>
                    <Select.Popup>
                        <Select.Item value="none">{ t("noAdditionalProof") }</Select.Item>
                        { factorTokens.map(token => <Select.Item key={ token } value={ token }>{ tokenLabels[token] }</Select.Item>) }
                    </Select.Popup>
                </Select.Root>
            </Field>
            : <Collapsible.Root>
                <Stack gap="inline">
                    <Collapsible.Trigger className="w-full justify-between">
                        { t("additionalProofsOptionalCount", { count: extraTokens.length }) }
                    </Collapsible.Trigger>
                    <Collapsible.Panel>
                        <Fieldset legend={ t("additionalProofsOptional") } disabled={ busy }>
                            <Stack gap="inline">
                                { tokenKinds.filter(token => token !== primary).map(token => <Field key={ token } label={ tokenLabels[token] }>
                                    <Checkbox checked={ extraTokens.includes(token) } disabled={ busy }
                                        onCheckedChange={ checked => { toggleExtraToken(token, checked); } } />
                                </Field>) }
                            </Stack>
                        </Fieldset>
                    </Collapsible.Panel>
                </Stack>
            </Collapsible.Root> }
    </Stack>;
}

export function SchemeEditor({
    t, actionLabel, tokenLabels, scheme, editing, busy, validationError, onChange, onCancel, onSave,
}: SchemeEditorProps) {
    const isSignIn = scheme.action === "SIGN_IN";
    const primary = isSignIn
        ? scheme.requiredTokens.find(token => primaryTokens.includes(token)) ?? "PASSWORD"
        : scheme.requiredTokens[0] ?? "PASSWORD";
    const extraTokens = scheme.requiredTokens.filter(token => token !== primary);
    const factor = extraTokens.find(token => factorTokens.includes(token));
    const validRank = Number.isInteger(scheme.assuranceRank) && scheme.assuranceRank > 0;
    const validSignIn = !isSignIn || (
        scheme.requiredTokens.filter(token => primaryTokens.includes(token)).length === 1 && extraTokens.length <= 1
    );
    const canSave = !busy && validRank && scheme.requiredTokens.length > 0 && validSignIn;

    const setPrimary = (value: string | null) => {
        if (!value || !tokenKinds.includes(value as TokenKind)) return;
        const remaining = scheme.requiredTokens.filter(token => token !== primary && token !== value);
        onChange({ requiredTokens: [value as TokenKind, ...remaining] });
    };

    const setFactor = (value: string | null) => {
        if (!value) return;
        onChange({ requiredTokens: [primary, ...(value === "none" ? [] : [value as TokenKind])] });
    };

    const toggleExtraToken = (token: TokenKind, checked: boolean) => {
        const remaining = extraTokens.filter(item => item !== token);
        onChange({ requiredTokens: [primary, ...(checked ? [...remaining, token] : remaining)] });
    };

    return <Stack gap="stack">
        <Button tone="ghost" className="self-start" disabled={ busy } onClick={ onCancel }>{ t("backToActionSchemes") }</Button>
        <Stack gap="inline-tight">
            <Text variant="title2" as="h2">{ t(editing ? "editPath" : "newPathTitle") }</Text>
            <Text variant="body" color="secondary">{ t("schemeEditorHelp", { action: actionLabel }) }</Text>
        </Stack>

        <Stack gap="stack" className="max-w-2xl">
            <SchemeProofFields t={ t } tokenLabels={ tokenLabels } isSignIn={ isSignIn } primary={ primary }
                factor={ factor } extraTokens={ extraTokens } busy={ busy } setPrimary={ setPrimary }
                setFactor={ setFactor } toggleExtraToken={ toggleExtraToken } />

            <Panel variant="inset">
                <Stack gap="inline-tight">
                    <Text variant="bodyStrong">{ t("pathResult", { path: schemeTitle(scheme, tokenLabels) }) }</Text>
                    <Text variant="small" color="secondary">{ t("proofsAnded") }</Text>
                </Stack>
            </Panel>

            <Collapsible.Root>
                <Stack gap="inline">
                    <Collapsible.Trigger className="w-full justify-between">{ t("advancedSchemeSettings") }</Collapsible.Trigger>
                    <Collapsible.Panel>
                        <Field label={ t("assuranceRank") } description={ t("rankHelp") }>
                            <Input
                                type="number"
                                min={ 1 }
                                step={ 1 }
                                value={ scheme.assuranceRank }
                                disabled={ busy }
                                onChange={ event => { onChange({ assuranceRank: Number(event.target.value) }); } }
                            />
                        </Field>
                        { !validRank && <Text variant="small" role="alert" tone="danger">{ t("rankRequired") }</Text> }
                    </Collapsible.Panel>
                </Stack>
            </Collapsible.Root>

            { validationError && <Text variant="small" role="alert" tone="danger">{ validationError }</Text> }
            { !validSignIn && <Text variant="small" role="alert" tone="danger">{ t("signInCompositionError") }</Text> }
            <Row gap="inline" className="flex-wrap">
                <Button tone="neutral" disabled={ busy } onClick={ onCancel}>{ t("cancel") }</Button>
                <Button tone="primary" disabled={ !canSave } onClick={ onSave}>{ t("saveSchemeToDraft") }</Button>
            </Row>
        </Stack>
    </Stack>;
}

export function schemeTitle(scheme: Pick<Scheme, "requiredTokens">, labels: TokenLabels): string {
    return tokenKinds.filter(token => scheme.requiredTokens.includes(token)).map(token => labels[token]).join(" + ");
}
