"use client";

import { useState } from "react";
import { useApi } from "frontend-shared/api";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Stack } from "frontend-shared/ui/stack";
import { authStrings, type AuthTexts } from "../../../shared/texts";
import { GoogleSignIn } from "../../../entities/viewer";

export interface SignInWithGoogleButtonProps {
    readonly texts: AuthTexts;
}

/** The whole page navigates to Google; the server brings the person back to `/login/continue`. */
export function SignInWithGoogleButton({ texts }: SignInWithGoogleButtonProps) {
    const t = authStrings(texts, "auth");
    const login = authStrings(texts, "login");
    const api = useApi();
    const [state, setState] = useState<"idle" | "starting" | "failed">("idle");

    const start = async () => {
        setState("starting");
        try {
            window.location.assign(await new GoogleSignIn(api).begin());
        } catch {
            setState("failed");
        }
    };

    return (
        <Stack gap="stack">
            <Button tone="neutral" size="lg" loading={state === "starting"} onClick={() => void start()} className="w-full">
                {t("continueWithGoogle")}
            </Button>
            {state === "failed" ? <Callout tone="danger">{login("failed")}</Callout> : null}
        </Stack>
    );
}
