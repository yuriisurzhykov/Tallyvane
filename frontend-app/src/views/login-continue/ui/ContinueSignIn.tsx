"use client";

import { useState } from "react";
import { Callout } from "frontend-shared/ui/callout";
import { Link } from "frontend-shared/ui/link";
import { Spinner } from "frontend-shared/ui/spinner";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useSignInState } from "@/entities/sign-in";
import { OpenSession } from "@/features/open-session";
import { useFinishSignIn } from "@/features/reauthenticate";
import { useStrings } from "@/shared/i18n";
import { Redirect } from "@/shared/navigation";

/**
 * Google has sent the person back. This page only decides, from where the server says the sign-in stands:
 * everything proved opens the session and goes on (or, in a re-sign-in window, reports back and closes); a
 * code still wanted goes to `/login/verify`; anything else says what to do. It never remembers the answer,
 * so a refresh or the back button reads it again.
 */
export function ContinueSignIn() {
    const t = useStrings("continue");
    const finish = useFinishSignIn();
    const { status } = useSignInState();
    const [reported, setReported] = useState(false);

    if (reported) {
        return <Text variant="body">{t("closeTab")}</Text>;
    }

    switch (status.status) {
        case "loading":
            return <Spinner size="lg" label={t("working")} />;
        case "failed":
            return <Problem message={t("failed")} />;
        case "none":
            return <Problem message={t("ended")} />;
        case "ready":
            return status.state.proceed({
                open: () => <OpenSession onOpened={() => { setReported(finish()); }} />,
                answer: () => <Redirect to="/login/verify" label={t("working")} />,
                startAgain: () => <Problem message={t("ended")} />,
                blocked: () => <Problem message={t("blocked")} />,
            });
    }
}

function Problem({ message }: { readonly message: string }) {
    const t = useStrings("continue");
    return (
        <Stack gap="stack">
            <Callout tone="danger">{message}</Callout>
            <Link href="/login">{t("back")}</Link>
        </Stack>
    );
}
