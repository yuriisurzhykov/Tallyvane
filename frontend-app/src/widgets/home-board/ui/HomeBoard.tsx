"use client";

import { useRef, type ReactNode } from "react";
import { Callout } from "frontend-shared/ui/callout";
import { Composer } from "frontend-shared/ui/composer";
import { Heading } from "frontend-shared/ui/heading";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { Story } from "@/entities/story";
import { useStrings } from "@/shared/i18n";
import { useHomeBoard } from "../model/useHomeBoard";
import { RouteCard } from "./RouteCard";
import { StepCard } from "./StepCard";
import { StoryCard } from "./StoryCard";
import { Understood } from "./Understood";

export interface HomeBoardProps {
    /** Draws the frame around the page and its pinned composer; the board owns only what goes in them. */
    readonly frame: (parts: { readonly dock: ReactNode; readonly children: ReactNode }) => ReactNode;
}

/**
 * The home (design variant E): the route to the goal as a strip, one main step, the stories told so far, and
 * the field pinned below. Nothing here suggests a vacancy (ADR-040).
 */
export function HomeBoard({ frame }: HomeBoardProps) {
    const t = useStrings("home");
    const board = useHomeBoard();
    const field = useRef<HTMLTextAreaElement>(null);
    const { home, kept } = board;

    const dock = (
        <Composer
            label={t("whatsNew")}
            placeholder={board.continuing === undefined ? t("whatsNew") : t("answerHere")}
            value={board.words}
            maxLength={20000}
            busy={board.busy}
            fieldRef={field}
            submitLabel={t("send")}
            onChange={board.setWords}
            onSubmit={() => void board.send()}
        />
    );

    return frame({
        dock,
        children: (
            <Stack gap="section-gap">
                {board.notice !== null ? <Callout tone="attention">{board.notice}</Callout> : null}
                {home === null ? (
                    board.notice === null ? <Text variant="body" color="secondary">{t("loading")}</Text> : null
                ) : (
                    <>
                        {kept !== null ? <Understood story={kept} onDone={board.dismissKept} /> : null}
                        <RouteCard home={home} />
                        <StepCard home={home} onChoose={(role) => void board.choose(role)} onAnswer={() => field.current?.focus()} />
                        <StoryList told={board.told} />
                    </>
                )}
            </Stack>
        ),
    });
}

function StoryList({ told }: { readonly told: readonly Story[] }) {
    const t = useStrings("home");
    return (
        <Stack gap="stack">
            <Stack gap="inline-tight">
                <Heading level={2} variant="title3">{t("stories")}</Heading>
                <Text variant="small" color="secondary">{told.length === 0 ? t("noStories") : t("storiesLead")}</Text>
            </Stack>
            {told.map((story) => (
                <StoryCard key={story.id} story={story} />
            ))}
        </Stack>
    );
}
