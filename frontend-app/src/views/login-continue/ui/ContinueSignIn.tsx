"use client";

import { useCallback, useState } from "react";
import { useRouter } from "next/navigation";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import { ReturnPath } from "@/entities/viewer";
import { OpenSession } from "@/features/open-session";
import { PopupHandoff } from "@/features/reauthenticate";

/**
 * Google has sent the person back. Opens the session, then either goes where they were headed, or, when
 * this window was opened only to sign in again over a page that is still waiting, reports back and closes.
 */
export function ContinueSignIn() {
    const t = useStrings("continue");
    const router = useRouter();
    const [reported, setReported] = useState(false);

    const opened = useCallback(() => {
        if (new PopupHandoff().completeIfExpected()) {
            setReported(true);
            window.close();
            return;
        }
        router.replace(new ReturnPath().take() ?? "/today");
    }, [router]);

    return reported ? <Text variant="body">{t("closeTab")}</Text> : <OpenSession onOpened={opened} />;
}
