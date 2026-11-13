import { Badge } from "frontend-shared/ui/badge";
import { Row } from "frontend-shared/ui/row";
import { Heading } from "frontend-shared/ui/heading";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import type { Story } from "@/entities/story";
import { useStrings } from "@/shared/i18n";

/**
 * One story, with the facts taken from it and the words each fact quotes. The quote under a fact is the
 * product's first law made visible: nothing is kept that the person did not say.
 */
export function StoryCard({ story, title }: { readonly story: Story; readonly title?: string }) {
    const t = useStrings("home");
    const period = [story.started, story.ended].filter(Boolean).join("–");
    const where = [story.company, period].filter(Boolean).join(" · ");
    return (
        <Surface variant="elevated" className="p-group-gap">
            <Stack gap="stack">
                <Stack gap="inline-tight">
                    <Row gap="inline" className="flex-wrap justify-between">
                        <Heading level={3} variant="bodyStrong">{title ?? story.title ?? t("untitled")}</Heading>
                        <Badge tone={story.state === "open" ? "attention" : "success"}>{t(story.state)}</Badge>
                    </Row>
                    {where !== "" ? <Text variant="small" color="secondary">{where}</Text> : null}
                </Stack>
                {story.facts.map((fact) => (
                    <Stack key={fact.id} gap="inline-tight">
                        <Text variant="body">{fact.text}</Text>
                        {fact.quotes.length > 0 ? (
                            <Text variant="small" color="muted">{`“${fact.quotes.join(" … ")}”`}</Text>
                        ) : null}
                    </Stack>
                ))}
            </Stack>
        </Surface>
    );
}
