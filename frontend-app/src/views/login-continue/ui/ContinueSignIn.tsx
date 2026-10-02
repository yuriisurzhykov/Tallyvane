"use client";

import { useState } from "react";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import { OpenSession } from "@/features/open-session";
import { useFinishSignIn } from "@/features/reauthenticate";

/** Google has sent the person back: open the session, then go on (or, in a re-sign-in window, report back and close). */
export function ContinueSignIn() {
    const t = useStrings("continue");
    const finish = useFinishSignIn();
    const [reported, setReported] = useState(false);

    return reported ? (
        <Text variant="body">{t("closeTab")}</Text>
    ) : (
        <OpenSession onOpened={() => { setReported(finish()); }} />
    );
}
