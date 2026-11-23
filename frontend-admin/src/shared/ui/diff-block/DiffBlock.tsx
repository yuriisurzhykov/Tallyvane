import { Fragment } from "react";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { VisuallyHidden } from "frontend-shared/ui/visually-hidden";
import type { Diff } from "../../diff";
import { useStrings } from "../../i18n";

export interface DiffBlockProps {
    readonly diff: Diff;
}

/** A list of differences drawn as the design canvas draws one: a heading per part, what was there in red, what is there now in green. */
export function DiffBlock({ diff }: DiffBlockProps) {
    const t = useStrings("diff");

    return (
        <Surface variant="inset" className="p-stack">
            <Stack gap="inline-tight">
                {diff.map((line, at) => (
                    <Fragment key={at}>
                        {line.appearAs({
                            section: (text) => <Text variant="overline" color="muted">{text}</Text>,
                            removed: (text) => (
                                <Row gap="inline" className="rounded-control bg-status-danger-subtle px-inline py-inline-tight">
                                    <Text variant="numeric" tone="danger" aria-hidden="true">{t("removedMark")}</Text>
                                    <VisuallyHidden>{t("removed")}</VisuallyHidden>
                                    <Text variant="numeric" tone="danger">{text}</Text>
                                </Row>
                            ),
                            added: (text) => (
                                <Row gap="inline" className="rounded-control bg-status-success-subtle px-inline py-inline-tight">
                                    <Text variant="numeric" tone="success" aria-hidden="true">{t("addedMark")}</Text>
                                    <VisuallyHidden>{t("added")}</VisuallyHidden>
                                    <Text variant="numeric" tone="success">{text}</Text>
                                </Row>
                            ),
                        })}
                    </Fragment>
                ))}
            </Stack>
        </Surface>
    );
}
