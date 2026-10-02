"use client";

import { useState } from "react";
import { useApi } from "frontend-shared/api";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Stack } from "frontend-shared/ui/stack";
import { useStrings } from "@/shared/i18n";
import { GoogleSignIn } from "@/entities/viewer";

/** The whole page navigates to Google; the server brings the person back to `/login/continue`. */
export function SignInWithGoogleButton() {
    const t = useStrings("auth");
    const login = useStrings("login");
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
