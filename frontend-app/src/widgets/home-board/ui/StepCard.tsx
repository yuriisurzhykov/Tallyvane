import { Button } from "frontend-shared/ui/button";
import { Row } from "frontend-shared/ui/row";
import { Heading } from "frontend-shared/ui/heading";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import type { Home } from "@/entities/career";
import { useStrings } from "@/shared/i18n";

type Strings = ReturnType<typeof useStrings<"home">>;

export interface StepCardProps {
    readonly home: Home;
    readonly onChoose: (role: string) => void;
    readonly onAnswer: () => void;
}

/** The one thing worth doing now, said once. Everything else on the page is quieter than this. */
export function StepCard({ home, onChoose, onAnswer }: StepCardProps) {
    const t = useStrings("home");
    return (
        <Surface variant="elevated" className="p-group-gap">
            <Stack gap="stack-tight">
                {home.step === "close_gap" ? (
                    <Text variant="overline" tone="attention">{t("closeGapEyebrow")}</Text>
                ) : (
                    <Text variant="overline" color="secondary">{t("nextStep")}</Text>
                )}
                <Heading level={2} variant="title2">{stepTitle(home, t)}</Heading>
                {home.step === "close_gap" && home.goalName ? (
                    <Text variant="small" color="secondary">{t("whyGap", { goal: home.goalName })}</Text>
                ) : null}
                <Row gap="inline" className="flex-wrap pt-stack-tight">
                    {home.step === "set_goal" ? (
                        home.roles.map((role) => (
                            <Button key={role.slug} tone="neutral" onClick={() => { onChoose(role.slug); }}>
                                {role.name}
                            </Button>
                        ))
                    ) : (
                        <Button tone="primary" onClick={onAnswer}>{home.step === "finish_story" ? t("answer") : t("tell")}</Button>
                    )}
                </Row>
            </Stack>
        </Surface>
    );
}

function stepTitle(home: Home, t: Strings): string {
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
