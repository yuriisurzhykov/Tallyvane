"use client";

import { useEffect, useRef, type ReactNode } from "react";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "../../i18n";

/** Long enough for a part of a form that opens with a short transition to be open. */
const SETTLE_MS = 300;

export interface LimitRowProps {
    /** What the limit is called. */
    readonly name: string;
    /** One sentence saying what the limit is for. */
    readonly sentence: string;
    /** The limit in force now, as a person says it; shown beside the input so a change is made knowing the old value. */
    readonly now?: string;
    /** What the server said is wrong with this limit, in words. */
    readonly error?: string;
    /** The one input: the only thing in the row a person types into. */
    readonly children: ReactNode;
}

/** One limit of a version: its name and what it is for, then the single input, then the value in force. Always in this order, whatever the width, so every limit reads the same way. */
export function LimitRow({ name, sentence, now, error, children }: LimitRowProps) {
    const t = useStrings("limit");
    const fault = useRef<HTMLSpanElement>(null);
    // A fault on a limit may be below the fold of the drawer; bring it into view when it appears. The wait lets a
    // collapsed part that the fault opens finish opening first, so the fault is at its place when the page scrolls to it.
    useEffect(() => {
        if (error === undefined) return undefined;
        const timer = window.setTimeout(() => { fault.current?.scrollIntoView({ block: "nearest" }); }, SETTLE_MS);
        return () => { window.clearTimeout(timer); };
    }, [error]);

    return (
        <Surface variant="inset" className="p-stack">
            <Stack gap="stack-tight">
                <Stack gap="inline-tight">
                    <Text variant="bodyStrong">{name}</Text>
                    <Text variant="small" color="muted">{sentence}</Text>
                </Stack>
                {children}
                {now !== undefined ? <Text variant="caption" color="muted">{t("now", { value: now })}</Text> : null}
                {error !== undefined ? <Text ref={fault} variant="caption" tone="danger" role="alert">{error}</Text> : null}
            </Stack>
        </Surface>
    );
}
