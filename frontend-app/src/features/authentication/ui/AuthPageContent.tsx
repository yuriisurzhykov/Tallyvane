"use client";

import { Text } from "frontend-shared/ui/text";
import { renderAuthenticationStep } from "@/widgets/authentication-step";
import { useAuth } from "../model/AuthContext";
import { useAuthStepProps } from "../lib/useAuthStepProps";
import styles from "../../../widgets/authentication-step/ui/auth-step.module.css";

export function AuthPageContent() {
    const { kind, state, t } = useAuth();
    const stepProps = useAuthStepProps();

    const isPreview = kind === "preview";
    const displayedKind = isPreview ? state.preview : kind;

    if (isPreview && displayedKind === "callback") {
        return <Text variant="body">{t("previewActionNotice")}</Text>;
    }

    return (
        <>
            {state.notice && (
                <Text as="p" variant="body" role="status" className={styles.notice ?? ""}>
                    {state.notice}
                </Text>
            )}
            {renderAuthenticationStep(displayedKind, stepProps)}
        </>
    );
}