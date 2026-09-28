"use client";

import { Link } from "frontend-shared/ui/link";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useAuth } from "../model/AuthContext";
import type { AuthStringKey } from "../model/strings";
import styles from "../../../widgets/authentication-step/ui/auth-step.module.css";

export function AuthPageFooter() {
    const { kind, state: { primaryMethods }, t } = useAuth();

    const links = kind === "login" ? [
        ["noAccountPrompt", "/register", "createAccountLink"],
        ["", "/forgot-password", "forgotPasswordLink"],
        ...(primaryMethods.includes("EMAIL_SIGN_IN_CODE")
            ? [["", "/otp?purpose=login", "signInEmailCodeLink"]]
            : []),
    ] : kind === "register" ? [
        ["hasAccountPrompt", "/login", "signInLink"]
    ] : [];

    if (links.length === 0) return null;

    return (
        <Stack as="footer" gap="inline" className={styles.bottom ?? ""}>
            {links.map(([prompt, href, label]) => (
                <Text key={href} variant="body">
                    {prompt && <>{t(prompt as AuthStringKey)} </>}
                    <Link className={styles.link ?? ""} href={href ?? "/login"}>
                        {t(label as AuthStringKey)}
                    </Link>
                </Text>
            ))}
        </Stack>
    );
}