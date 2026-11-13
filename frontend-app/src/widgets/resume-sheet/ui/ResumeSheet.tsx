"use client";

import { useEffect, useMemo, useState } from "react";
import { useApi } from "frontend-shared/api";
import { Badge } from "frontend-shared/ui/badge";
import { Dot } from "frontend-shared/ui/dot";
import { Heading } from "frontend-shared/ui/heading";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { Career, type Resume } from "@/entities/career";
import { useStrings } from "@/shared/i18n";

/** The résumé the server selected from the person's own facts: every line traces to their words. */
export function ResumeSheet() {
    const t = useStrings("resume");
    const api = useApi();
    const career = useMemo(() => new Career(api), [api]);
    const [resume, setResume] = useState<Resume | null>(null);
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        let current = true;
        career.resume().then(
            (shown) => { if (current) setResume(shown); },
            () => { if (current) setFailed(true); },
        );
        return () => { current = false; };
    }, [career]);

    if (resume === null) return <Text variant="body" color="secondary">{failed ? t("loadFailed") : t("loading")}</Text>;
    if (resume.roles.length === 0) return <Text variant="body" color="secondary">{t("empty")}</Text>;

    return (
        <Stack gap="group-gap">
            <Surface variant="elevated" className="p-group-gap">
                <Stack gap="group-gap">
                    {resume.roles.map((role, index) => (
                        <Stack key={index} gap="stack-tight">
                            <Stack gap="inline-tight">
                                <Heading level={2} variant="title3">
                                    {[role.position, role.company].filter(Boolean).join(" · ") || t("role")}
                                </Heading>
                                {role.period ? <Text variant="small" color="secondary">{role.period}</Text> : null}
                            </Stack>
                            {role.bullets.map((bullet) => (
                                <Row key={bullet} gap="stack-tight" className="items-start">
                                    <Dot tone="neutral" className="mt-stack-tight shrink-0" />
                                    <Text variant="body">{bullet}</Text>
                                </Row>
                            ))}
                        </Stack>
                    ))}
                </Stack>
            </Surface>
            {resume.skills.length > 0 ? (
                <Stack gap="stack-tight">
                    <Text variant="overline" color="secondary">{t("skillsTitle")}</Text>
                    <Row gap="inline" className="flex-wrap">
                        {resume.skills.map((skill) => (
                            <Badge key={skill} tone="neutral">{skill}</Badge>
                        ))}
                    </Row>
                </Stack>
            ) : null}
        </Stack>
    );
}
