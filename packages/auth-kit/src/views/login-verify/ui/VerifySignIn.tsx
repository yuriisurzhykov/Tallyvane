"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Callout } from "frontend-shared/ui/callout";
import { Link } from "frontend-shared/ui/link";
import { Spinner } from "frontend-shared/ui/spinner";
import { Stack } from "frontend-shared/ui/stack";
import { RecoveryNotice, useSignInState } from "../../../entities/sign-in";
import { ReturnPath } from "../../../entities/viewer";
import { SecondFactorForm } from "../../../features/answer-second-factor";
import { authStrings, type AuthTexts } from "../../../shared/texts";
import { Redirect } from "../../../shared/navigation";
import { RecoveryCodeUsed } from "./RecoveryCodeUsed";

const CONTINUE = "/login/continue";

export interface VerifySignInProps {
    readonly texts: AuthTexts;
    /** Where to send the person to set the authenticator up again after a recovery code; `undefined` when nowhere. */
    readonly recoverySetUpAt: string | undefined;
}

/**
 * Asks for the second step of a sign-in. It reads where the sign-in stands and shows the field only while a
 * code is wanted; once the code is right it hands back to `/login/continue`, which reads the state again and
 * opens the session. A recovery code retires the authenticator (ADR-093), so that case stops on a notice first,
 * which is remembered in this tab until it is read so that a reload does not skip it.
 */
export function VerifySignIn({ texts, recoverySetUpAt }: VerifySignInProps) {
    const t = authStrings(texts, "verify");
    const router = useRouter();
    const { status, refresh } = useSignInState();
    const [over, setOver] = useState(false);
    const [notice] = useState(() => new RecoveryNotice());

    if (over) {
        return <Ended texts={texts} message={t("ended")} />;
    }

    switch (status.status) {
        case "loading":
            return <Spinner size="lg" label={t("working")} />;
        case "failed":
            return <Ended texts={texts} message={t("failed")} />;
        case "none":
            return <Ended texts={texts} message={t("ended")} />;
        case "ready":
            return status.state.proceed({
                answer: (offer) => (
                    <SecondFactorForm
                        texts={texts}
                        offer={offer}
                        onAccepted={(left) => {
                            if (left === undefined) {
                                router.replace(CONTINUE);
                            } else {
                                notice.remember(left);
                                refresh();
                            }
                        }}
                        onOver={() => { setOver(true); }}
                        onChanged={refresh}
                    />
                ),
                open: () => {
                    const codesLeft = notice.pending();
                    return codesLeft === undefined ? <Redirect to={CONTINUE} label={t("working")} /> : (
                        <RecoveryCodeUsed
                            texts={texts}
                            codesLeft={codesLeft}
                            onContinue={() => {
                                notice.read();
                                router.replace(CONTINUE);
                            }}
                            onSetUp={
                                recoverySetUpAt === undefined
                                    ? undefined
                                    : () => {
                                          notice.read();
                                          new ReturnPath().remember(recoverySetUpAt);
                                          router.replace(CONTINUE);
                                      }
                            }
                        />
                    );
                },
                startAgain: () => <Ended texts={texts} message={t("ended")} />,
                blocked: () => <Ended texts={texts} message={t("blocked")} />,
            });
    }
}

function Ended({ texts, message }: { readonly texts: AuthTexts; readonly message: string }) {
    const t = authStrings(texts, "verify");
    return (
        <Stack gap="stack">
            <Callout tone="danger">{message}</Callout>
            <Link href="/login">{t("startAgain")}</Link>
        </Stack>
    );
}
