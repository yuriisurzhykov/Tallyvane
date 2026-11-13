"use client";

import { useEffect, useMemo, useState } from "react";
import { useApi } from "frontend-shared/api";
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

    if (resume === null) return <Text variant="body">{failed ? t("loadFailed") : t("loading")}</Text>;
    if (resume.roles.length === 0) return <Text variant="body">{t("empty")}</Text>;

    return (
        <Surface variant="elevated">
            <Stack gap="section-gap">
                {resume.roles.map((role, index) => (
                    <Stack key={index} gap="stack-tight">
                        <Text variant="title3">{[role.position, role.company].filter(Boolean).join(" · ") || t("role")}</Text>
                        {role.period ? <Text variant="small">{role.period}</Text> : null}
                        {role.bullets.map((bullet) => (
                            <Text key={bullet} variant="body">{`• ${bullet}`}</Text>
                        ))}
                    </Stack>
                ))}
                {resume.skills.length > 0 ? <Text variant="small">{t("skills", { skills: resume.skills.join(", ") })}</Text> : null}
            </Stack>
        </Surface>
    );
}
