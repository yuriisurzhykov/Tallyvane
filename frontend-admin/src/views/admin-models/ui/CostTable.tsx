import { Heading } from "frontend-shared/ui/heading";
import { Numeric } from "frontend-shared/ui/numeric";
import { Row } from "frontend-shared/ui/row";
import { Separator } from "frontend-shared/ui/separator";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import type { ModelsScreen } from "@/entities/model-provider";
import { useStrings } from "@/shared/i18n";

/** What the last 30 days cost, by provider, model and spec, so providers can be compared on price. */
export function CostTable({ costs }: { readonly costs: ModelsScreen["costs"] }) {
    const t = useStrings("models");
    const total = costs.reduce((sum, line) => sum + line.dollars, 0);
    return (
        <Stack gap="stack">
            <Row gap="stack" className="items-end justify-between">
                <Stack gap="inline-tight">
                    <Heading level={2} variant="title3">{t("costTitle")}</Heading>
                    <Text variant="small" color="secondary">{t("costLead")}</Text>
                </Stack>
                <Numeric>{`$${total.toFixed(4)}`}</Numeric>
            </Row>
            {costs.length === 0 ? <Text variant="body" color="secondary">{t("noCalls")}</Text> : (
                <Surface variant="elevated">
                    {costs.map((line, index) => (
                        <Stack key={`${line.provider}|${line.model}|${line.spec}`} gap="stack">
                            {index > 0 ? <Separator /> : null}
                            <Row gap="stack" className="justify-between px-group-gap pb-stack first:pt-stack">
                                <Stack gap="inline-tight">
                                    <Text variant="bodyStrong">{`${line.provider} · ${line.model}`}</Text>
                                    <Text variant="small" color="secondary">{line.spec}</Text>
                                </Stack>
                                <Stack gap="inline-tight">
                                    <Numeric>{`$${line.dollars.toFixed(4)}`}</Numeric>
                                    <Text variant="small" color="secondary">{t("callsLine", { calls: line.calls, remembered: line.remembered })}</Text>
                                    <Text variant="small" color="secondary">{t("tokensLine", { tokensIn: line.tokensIn, tokensOut: line.tokensOut })}</Text>
                                </Stack>
                            </Row>
                        </Stack>
                    ))}
                </Surface>
            )}
        </Stack>
    );
}
