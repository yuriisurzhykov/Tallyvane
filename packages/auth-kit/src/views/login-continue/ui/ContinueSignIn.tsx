"use client";

import { useState } from "react";
import { Callout } from "frontend-shared/ui/callout";
import { Link } from "frontend-shared/ui/link";
import { Spinner } from "frontend-shared/ui/spinner";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useSignInState } from "../../../entities/sign-in";
import { OpenSession } from "../../../features/open-session";
import { useFinishSignIn } from "../../../features/reauthenticate";
import { authStrings, type AuthTexts } from "../../../shared/texts";
import { Redirect } from "../../../shared/navigation";

export interface ContinueSignInProps {
    readonly texts: AuthTexts;
    readonly landing: string;
}

/**
 * Google has sent the person back. This page only decides, from where the server says the sign-in stands:
 * everything proved opens the session and goes on (or, in a re-sign-in window, reports back and closes); a
 * code still wanted goes to `/login/verify`; anything else says what to do. It never remembers the answer,
 * so a refresh or the back button reads it again.
 */
export function ContinueSignIn({ texts, landing }: ContinueSignInProps) {
    const t = authStrings(texts, "continue");
    const finish = useFinishSignIn(landing);
    const { status } = useSignInState();
    const [reported, setReported] = useState(false);

    if (reported) {
        return <Text variant="body">{t("closeTab")}</Text>;
    }

    switch (status.status) {
        case "loading":
            return <Spinner size="lg" label={t("working")} />;
        case "failed":
            return <Problem texts={texts} message={t("failed")} />;
        case "none":
            return <Problem texts={texts} message={t("ended")} />;
        case "ready":
            return status.state.proceed({
                open: () => <OpenSession texts={texts} onOpened={() => { setReported(finish()); }} />,
                answer: () => <Redirect to="/login/verify" label={t("working")} />,
                startAgain: () => <Problem texts={texts} message={t("ended")} />,
                blocked: () => <Problem texts={texts} message={t("blocked")} />,
            });
    }
}

function Problem({ texts, message }: { readonly texts: AuthTexts; readonly message: string }) {
    const t = authStrings(texts, "continue");
    return (
        <Stack gap="stack">
            <Callout tone="danger">{message}</Callout>
            <Link href="/login">{t("back")}</Link>
        </Stack>
    );
}
