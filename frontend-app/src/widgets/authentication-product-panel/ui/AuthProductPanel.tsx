"use client";

import { useAuthStrings } from "../../../features/authentication/model/strings";
import { Dot } from "frontend-shared/ui/dot";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import styles from "./auth-product-panel.module.css";

/** Illustration-only product context beside the auth form; its labels all come from auth i18n. */
export function AuthProductPanel() {
    const t = useAuthStrings("auth");
    return <Stack as="aside" gap="section-gap" className={ styles.panel ?? "" } aria-label={ t("preview") }>
        <Stack gap="stack-tight" className={ styles.copy ?? "" }>
            <Text variant="overline" color="muted">{ t("productEyebrow") }</Text>
            <Text variant="title2" as="h2" className={ styles.title ?? "" }>{ t("productTitle") }</Text>
            <Text variant="body" color="secondary">{ t("productDescription") }</Text>
        </Stack>
        <Stack gap="group-gap" className={ styles.preview ?? "" }>
            <Row gap="inline" className={ styles.previewHeader ?? "" }>
                <Text variant="title3">{ t("today") }</Text>
                <Text variant="small" className={ styles.previewMark ?? "" } aria-hidden="true">↗</Text>
            </Row>
            <Surface variant="elevated" className={ styles.taskCard ?? "" }>
                <Text variant="overline" color="muted">{ t("nextStep") }</Text>
                <Text variant="bodyStrong">{ t("previewTask") }</Text>
                <Text variant="small" color="muted">{ t("previewDetail") }</Text>
            </Surface>
            <Text variant="small" color="muted">{ t("pipeline") }</Text>
            <Row gap="inline" className={ styles.pipeline ?? "" }>
                <Text variant="small">{ t("saved") }</Text><Text variant="small">{ t("applied") }</Text><Text variant="small">{ t("interview") }</Text>
            </Row>
            <Surface variant="elevated" className={ styles.roleCard ?? "" }>
                <Text variant="small" className={ styles.companyMark ?? "" } aria-hidden="true">↗</Text>
                <Stack gap="inline-tight"><Text variant="bodyStrong">{ t("role") }</Text><Text variant="small" color="muted">{ t("company") }</Text></Stack>
                <Dot tone="success" className={ styles.statusDot ?? "" } />
            </Surface>
        </Stack>
        <Text variant="small" className={ styles.caption ?? "" }>{ t("previewLabel") }</Text>
    </Stack>;
}
