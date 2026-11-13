"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ProblemError, useApi } from "frontend-shared/api";
import { Badge } from "frontend-shared/ui/badge";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { TextArea } from "frontend-shared/ui/text-area";
import { Career, type Home } from "@/entities/career";
import { Stories, type Story } from "@/entities/story";
import { useStrings } from "@/shared/i18n";

const TONES: Record<"confirmed" | "growing" | "gap", "success" | "info" | "neutral"> = { confirmed: "success", growing: "info", gap: "neutral" };

/**
 * The home (design variant E): one main step on top, the route as a quiet strip, the stories below, and the
 * input pinned at the bottom. Nothing here suggests a vacancy (ADR-040).
 */
export function HomeBoard() {
    const t = useStrings("home");
    const api = useApi();
    const career = useMemo(() => new Career(api), [api]);
    const stories = useMemo(() => new Stories(api), [api]);
    const [home, setHome] = useState<Home | null>(null);
    const [told, setTold] = useState<Story[]>([]);
    const [words, setWords] = useState("");
    const [busy, setBusy] = useState(false);
    const [notice, setNotice] = useState<string | null>(null);
    const [reads, setReads] = useState(0);
    // A string, not `t`: the translator is a new function on every render and would refetch forever.
    const loadFailed = t("loadFailed");

    useEffect(() => {
        let current = true;
        Promise.all([career.home(), stories.list()]).then(
            ([shown, list]) => {
                if (!current) return;
                setHome(shown);
                setTold(list);
            },
            () => {
                if (current) setNotice(loadFailed);
            },
        );
        return () => {
            current = false;
        };
    }, [career, stories, reads, loadFailed]);

    const continuing = home?.step === "finish_story" ? (home.subject ?? undefined) : undefined;

    const send = useCallback(async () => {
        if (words.trim() === "") return;
        setBusy(true);
        setNotice(null);
        try {
            await stories.tell(words, continuing);
            setWords("");
            setReads((count) => count + 1);
        } catch (failure) {
            setNotice(failure instanceof ProblemError ? (failure.detail() ?? t("sendFailed")) : t("sendFailed"));
        } finally {
            setBusy(false);
        }
    }, [words, continuing, stories, t]);

    const choose = useCallback(
        async (role: string) => {
            await career.choose(role);
            setReads((count) => count + 1);
        },
        [career],
    );

    if (home === null) {
        return <Text variant="body">{notice ?? t("loading")}</Text>;
    }

    return (
        <Stack gap="section-gap">
            <StepCard home={home} onChoose={(role) => void choose(role)} />
            <RouteStrip home={home} />
            <StoryList told={told} />
            <Surface variant="primary">
                <Stack gap="stack-tight">
                    {notice !== null ? <Callout tone="attention">{notice}</Callout> : null}
                    <TextArea
                        aria-label={t("whatsNew")}
                        placeholder={continuing === undefined ? t("whatsNew") : t("answerHere")}
                        value={words}
                        maxLength={20000}
                        onChange={(event) => { setWords(event.target.value); }}
                    />
                    <Row gap="inline">
                        <Button tone="primary" loading={busy} disabled={busy || words.trim() === ""} onClick={() => void send()}>
                            {t("send")}
                        </Button>
                    </Row>
                </Stack>
            </Surface>
        </Stack>
    );
}

function StepCard({ home, onChoose }: { readonly home: Home; readonly onChoose: (role: string) => void }) {
    const t = useStrings("home");
    return (
        <Surface variant="elevated">
            <Stack gap="stack">
                <Text variant="small">{t("nextStep")}</Text>
                <Text variant="title2">{stepTitle(home, t)}</Text>
                {home.step === "set_goal" ? (
                    <Row gap="inline">
                        {home.roles.map((role) => (
                            <Button key={role.slug} tone="neutral" size="sm" onClick={() => { onChoose(role.slug); }}>
                                {role.name}
                            </Button>
                        ))}
                    </Row>
                ) : null}
            </Stack>
        </Surface>
    );
}

function RouteStrip({ home }: { readonly home: Home }) {
    const t = useStrings("home");
    if (home.route.length === 0) return null;
    return (
        <Stack gap="stack-tight">
            <Text variant="small">{t("route", { goal: home.goalName ?? "" })}</Text>
            <Row gap="inline-tight">
                {home.route.map((stop) => (
                    <Badge key={stop.skill} tone={TONES[stop.level]}>
                        {`${stop.name} · ${t(stop.level)}`}
                    </Badge>
                ))}
            </Row>
        </Stack>
    );
}

function StoryList({ told }: { readonly told: readonly Story[] }) {
    const t = useStrings("home");
    return (
        <Stack gap="stack">
            <Text variant="title3">{t("stories")}</Text>
            {told.length === 0 ? <Text variant="body">{t("noStories")}</Text> : null}
            {told.map((story) => (
                <Surface key={story.id} variant="inset">
                    <Stack gap="stack-tight">
                        <Row gap="inline">
                            <Text variant="bodyStrong">{story.title ?? t("untitled")}</Text>
                            {story.company ? <Text variant="small">{story.company}</Text> : null}
                            <Badge tone={story.state === "open" ? "attention" : "success"}>{t(story.state)}</Badge>
                        </Row>
                        {story.facts.map((fact) => (
                            <Text key={fact.id} variant="small" title={fact.quotes.join(" … ")}>
                                {`${fact.mine ? "●" : "○"} ${fact.text}`}
                            </Text>
                        ))}
                    </Stack>
                </Surface>
            ))}
        </Stack>
    );
}

function stepTitle(home: Home, t: ReturnType<typeof useStrings<"home">>): string {
    switch (home.step) {
        case "tell_first":
            return t("tellFirst");
        case "set_goal":
            return t("setGoal");
        case "finish_story":
            return t(`ask_${home.detail ?? "outcome"}` as "ask_outcome");
        case "close_gap":
            return t("closeGap", { skill: home.detail ?? "" });
        default:
            return t("keepGoing");
    }
}
