import type { ReactNode } from "react";
import { useViewer } from "auth-kit/entities/viewer";
import { Badge } from "frontend-shared/ui/badge";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import type { Listed } from "./Listed";
import { LOCALE, useAuthorWords } from "./authorWords";

export interface InForceCardProps {
    readonly version: Listed;
    /** What the version holds, drawn in full. */
    readonly summary: ReactNode;
}

/** The anchor of the screen: the version that judges every sign-in or session now, who put it in force and why. It has no acts; a new version starts from the page's own button. */
export function InForceCard({ version, summary }: InForceCardProps) {
    const t = useStrings("history");
    const viewer = useViewer();
    const authorWords = useAuthorWords();
    const now = version.inForce();
    const reason = now?.reason();
    const since =
        now === undefined
            ? undefined
            : reason === undefined
              ? t("inForceSince", { when: now.when(LOCALE), by: now.author(authorWords, viewer) })
              : t("inForceSinceWhy", { when: now.when(LOCALE), by: now.author(authorWords, viewer), reason });

    return (
        <Surface variant="elevated" className="p-group-gap">
            <Stack gap="group-gap">
                <Stack gap="stack-tight">
                    <Row gap="inline">
                        <Badge tone="success">{t("inForce")}</Badge>
                        <Text variant="numeric" color="secondary">{t("versionNumber", { number: version.number() })}</Text>
                    </Row>
                    <Text variant="title2">{version.made().reason() ?? t("initialVersion")}</Text>
                    {since !== undefined ? <Text variant="small" color="muted">{since}</Text> : null}
                </Stack>
                {summary}
            </Stack>
        </Surface>
    );
}
