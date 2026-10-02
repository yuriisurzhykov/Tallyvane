import { Callout } from "frontend-shared/ui/callout";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import { SignInWithGoogleButton } from "@/features/sign-in-with-google";
import { AuthFrame } from "@/widgets/auth-frame";
import { RememberReturnPath } from "./RememberReturnPath";

const PROBLEMS = {
    restart: "problemRestart",
    expired: "problemExpired",
    cancelled: "problemCancelled",
    refused: "problemRefused",
    "email-unverified": "problemEmailUnverified",
    unavailable: "problemUnavailable",
} as const;

export interface LoginPageProps {
    /** `?problem=` as the server's Google return sends it. Anything not on the list is ignored. */
    readonly problem: string | undefined;
    /** `?return=` as the console gate sends it. Checked before use. */
    readonly returnTo: string | undefined;
}

export function LoginPage({ problem, returnTo }: LoginPageProps) {
    const t = useStrings("login");
    const known = problem !== undefined && problem in PROBLEMS ? PROBLEMS[problem as keyof typeof PROBLEMS] : undefined;
    return (
        <AuthFrame title={t("title")} lead={t("lead")}>
            <RememberReturnPath path={returnTo} isRetry={problem !== undefined} />
            <Stack gap="stack">
                {known !== undefined ? <Callout tone="attention">{t(known)}</Callout> : null}
                <SignInWithGoogleButton />
                <Text variant="caption" color="muted">{t("fineprint")}</Text>
            </Stack>
        </AuthFrame>
    );
}
