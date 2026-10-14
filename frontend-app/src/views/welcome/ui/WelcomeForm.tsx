"use client";

import { useState } from "react";
import { useApi } from "frontend-shared/api";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import { Sessions } from "auth-kit/features/open-session";
import { useFinishSignIn } from "auth-kit/features/reauthenticate";
import { RegisterForm } from "@/features/register";

/** An account made, the person is signed in without going back to Google. */
export interface WelcomeFormProps {
    readonly initialName: string;
    readonly landing: string;
}

export function WelcomeForm({ initialName, landing }: WelcomeFormProps) {
    const t = useStrings("continue");
    const api = useApi();
    const finish = useFinishSignIn(landing);
    const [reported, setReported] = useState(false);

    const signIn = async () => {
        await new Sessions(api).open();
        setReported(finish());
    };

    return reported ? <Text variant="body">{t("closeTab")}</Text> : <RegisterForm initialName={initialName} onRegistered={signIn} />;
}
