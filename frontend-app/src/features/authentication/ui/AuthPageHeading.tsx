"use client";

import { Text } from "frontend-shared/ui/text";
import { useAuth } from "../model/AuthContext";
import { AUTH_HEADINGS } from "../lib/auth-headings";
import styles from "../../../widgets/authentication-step/ui/auth-step.module.css";

export function AuthPageHeading() {
    const { kind, state, t } = useAuth();

    const displayedKind = kind === "preview" ? state.preview : kind;
    const [titleKey, descriptionKey] = AUTH_HEADINGS[displayedKind];

    return (
        <>
            <Text as="h1" variant="title1" className={ styles.heading ?? "" }>
                { t(titleKey) }
            </Text>
            <Text as="p" variant="body" className={ styles.description ?? "" }>
                { t(descriptionKey) }
            </Text>
        </>
    );
}