"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Callout } from "frontend-shared/ui/callout";
import { Link } from "frontend-shared/ui/link";
import { Spinner } from "frontend-shared/ui/spinner";
import { Stack } from "frontend-shared/ui/stack";
import { useSignInState } from "@/entities/sign-in";
import { ReturnPath } from "@/entities/viewer";
import { SecondFactorForm } from "@/features/answer-second-factor";
import { useStrings } from "@/shared/i18n";
import { Redirect } from "@/shared/navigation";
import { RecoveryCodeUsed } from "./RecoveryCodeUsed";

const CONTINUE = "/login/continue";
const SECURITY = "/settings/security";

/**
 * Asks for the second step of a sign-in. It reads where the sign-in stands and shows the field only while a
 * code is wanted; once the code is right it hands back to `/login/continue`, which reads the state again and
 * opens the session. A recovery code retires the authenticator (ADR-093), so that case stops on a notice first.
 */
export function VerifySignIn() {
    const t = useStrings("verify");
    const router = useRouter();
    const { status, refresh } = useSignInState();
    const [over, setOver] = useState(false);
    const [codesLeft, setCodesLeft] = useState<number | undefined>(undefined);

    if (codesLeft !== undefined) {
        return (
            <RecoveryCodeUsed
                codesLeft={codesLeft}
                onContinue={() => { router.replace(CONTINUE); }}
                onSetUp={() => {
                    new ReturnPath().remember(SECURITY);
                    router.replace(CONTINUE);
                }}
            />
        );
    }
    if (over) {
        return <Ended message={t("ended")} />;
    }

    switch (status.status) {
        case "loading":
            return <Spinner size="lg" label={t("working")} />;
        case "failed":
            return <Ended message={t("failed")} />;
        case "none":
            return <Ended message={t("ended")} />;
        case "ready":
            return status.state.proceed({
                answer: (offer) => (
                    <SecondFactorForm
                        offer={offer}
                        onAccepted={(left) => {
                            if (left === undefined) {
                                router.replace(CONTINUE);
                            } else {
                                setCodesLeft(left);
                            }
                        }}
                        onOver={() => { setOver(true); }}
                        onChanged={refresh}
                    />
                ),
                open: () => <Redirect to={CONTINUE} label={t("working")} />,
                startAgain: () => <Ended message={t("ended")} />,
                blocked: () => <Ended message={t("blocked")} />,
            });
    }
}

function Ended({ message }: { readonly message: string }) {
    const t = useStrings("verify");
    return (
        <Stack gap="stack">
            <Callout tone="danger">{message}</Callout>
            <Link href="/login">{t("startAgain")}</Link>
        </Stack>
    );
}
