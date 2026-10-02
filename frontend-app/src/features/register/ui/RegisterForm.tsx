"use client";

import { useState } from "react";
import { ProblemError, useApi } from "frontend-shared/api";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Checkbox } from "frontend-shared/ui/checkbox";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Link } from "frontend-shared/ui/link";
import { Row } from "frontend-shared/ui/row";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import { Registrations } from "../api/Registrations";

const PRIVACY_URL = "https://tallyvane.com/privacy";
const TERMS_URL = "https://tallyvane.com/terms";

export interface RegisterFormProps {
    readonly initialName: string;
    /** Runs after the account exists; a failure here is shown like any other. */
    readonly onRegistered: () => Promise<void>;
}

interface Mistakes {
    readonly name?: string;
    readonly agreed?: string;
    readonly general?: string;
}

export function RegisterForm({ initialName, onRegistered }: RegisterFormProps) {
    const t = useStrings("welcome");
    const api = useApi();
    const [name, setName] = useState(initialName);
    const [agreed, setAgreed] = useState(false);
    const [busy, setBusy] = useState(false);
    const [mistakes, setMistakes] = useState<Mistakes>({});

    const submit = async () => {
        setBusy(true);
        setMistakes({});
        try {
            await new Registrations(api).register(name, agreed);
            await onRegistered();
        } catch (failure) {
            setMistakes(explain(failure, t));
            setBusy(false);
        }
    };

    return (
        <Form onFormSubmit={() => void submit()}>
            <Field label={t("name")} description={t("nameHint")} {...(mistakes.name ? { error: mistakes.name } : {})}>
                <Input name="name" value={name} onChange={(event) => { setName(event.target.value); }} autoComplete="name" />
            </Field>
            <Row gap="inline" className="items-start">
                <Checkbox aria-labelledby="consent-text" checked={agreed} onCheckedChange={setAgreed} />
                <Text id="consent-text" variant="small" color="secondary">
                    {t("consentLead")}
                    <Link href={PRIVACY_URL}>{t("privacyPolicy")}</Link>
                    {t("consentAnd")}
                    <Link href={TERMS_URL}>{t("terms")}</Link>
                    {t("consentTail")}
                </Text>
            </Row>
            {mistakes.agreed ? <Text variant="caption" tone="danger">{mistakes.agreed}</Text> : null}
            {mistakes.general ? <Callout tone="danger">{mistakes.general}</Callout> : null}
            <Button type="submit" tone="primary" size="lg" loading={busy} className="w-full">
                {t("submit")}
            </Button>
        </Form>
    );
}

function explain(failure: unknown, t: ReturnType<typeof useStrings<"welcome">>): Mistakes {
    if (failure instanceof ProblemError) {
        const name = failure.fieldCode("name") === "name-invalid" ? t("nameInvalid") : undefined;
        const agreed = failure.fieldCode("agreed") === "consent-required" ? t("consentRequired") : undefined;
        if (name !== undefined || agreed !== undefined) {
            return { ...(name ? { name } : {}), ...(agreed ? { agreed } : {}) };
        }
    }
    return { general: t("failed") };
}
