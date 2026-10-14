import { Callout } from "frontend-shared/ui/callout";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { authStrings, type AuthTexts, type SignInFrame } from "../../../shared/texts";
import { SignInWithGoogleButton } from "../../../features/sign-in-with-google";
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
    readonly texts: AuthTexts;
    /** The page the screen is drawn in; the app's own. */
    readonly frame: SignInFrame;
    /** `?problem=` as the server's Google return sends it. Anything not on the list is ignored. */
    readonly problem: string | undefined;
    /** `?return=` as the console gate sends it. Checked before use. */
    readonly returnTo: string | undefined;
}

export function LoginPage({ texts, frame: Frame, problem, returnTo }: LoginPageProps) {
    const t = authStrings(texts, "login");
    const known = problem !== undefined && problem in PROBLEMS ? PROBLEMS[problem as keyof typeof PROBLEMS] : undefined;
    return (
        <Frame title={t("title")} lead={t("lead")}>
            <RememberReturnPath path={returnTo} isRetry={problem !== undefined} />
            <Stack gap="stack">
                {known !== undefined ? <Callout tone="attention">{t(known)}</Callout> : null}
                <SignInWithGoogleButton texts={texts} />
                <Text variant="caption" color="muted">{t("fineprint")}</Text>
            </Stack>
        </Frame>
    );
}
