import { Button } from "frontend-shared/ui/button";
import { Icon } from "frontend-shared/ui/icon";
import { Row } from "frontend-shared/ui/row";
import { Heading } from "frontend-shared/ui/heading";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import type { Story } from "@/entities/story";
import { useStrings } from "@/shared/i18n";
import { StoryCard } from "./StoryCard";

/** Shown right after the person tells something: what was kept from it, before anything else asks for more. */
export function Understood({ story, onDone }: { readonly story: Story; readonly onDone: () => void }) {
    const t = useStrings("home");
    return (
        <Stack gap="stack">
            <Surface variant="elevated" className="p-group-gap">
                <Stack gap="stack-tight">
                    <Row gap="inline">
                        <Icon name="check" />
                        <Heading level={2} variant="title3">{t("understoodTitle")}</Heading>
                    </Row>
                    <Text variant="small" color="secondary">{t("understoodLead", { title: story.title ?? t("untitled") })}</Text>
                </Stack>
            </Surface>
            <StoryCard story={story} />
            <Row gap="inline">
                <Button tone="primary" onClick={onDone}>{t("done")}</Button>
            </Row>
        </Stack>
    );
}
