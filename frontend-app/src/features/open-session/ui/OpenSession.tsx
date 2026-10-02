"use client";

import { useEffect, useRef, useState } from "react";
import { useApi } from "frontend-shared/api";
import { Callout } from "frontend-shared/ui/callout";
import { Link } from "frontend-shared/ui/link";
import { Spinner } from "frontend-shared/ui/spinner";
import { Stack } from "frontend-shared/ui/stack";
import { useStrings } from "@/shared/i18n";
import { Sessions } from "../api/Sessions";

export interface OpenSessionProps {
    /** Called once, when the session exists. */
    readonly onOpened: () => void;
}

/** Opens the session as soon as it appears, and exactly once even when React runs effects twice. */
export function OpenSession({ onOpened }: OpenSessionProps) {
    const t = useStrings("continue");
    const api = useApi();
    const sent = useRef(false);
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        if (sent.current) {
            return;
        }
        sent.current = true;
        new Sessions(api).open().then(onOpened, () => {
            setFailed(true);
        });
    }, [api, onOpened]);

    if (failed) {
        return (
            <Stack gap="stack">
                <Callout tone="danger">{t("failed")}</Callout>
                <Link href="/login">{t("back")}</Link>
            </Stack>
        );
    }
    return <Spinner size="lg" label={t("working")} />;
}
