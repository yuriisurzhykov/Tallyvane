import { Badge } from "frontend-shared/ui/badge";
import { Button } from "frontend-shared/ui/button";
import { Heading } from "frontend-shared/ui/heading";
import { KeyValueList } from "frontend-shared/ui/key-value-list";
import { Row } from "frontend-shared/ui/row";
import { Separator } from "frontend-shared/ui/separator";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import type { ModelProvider } from "@/entities/model-provider";
import { useStrings } from "@/shared/i18n";

export interface ProviderCardsProps {
    readonly providers: readonly ModelProvider[];
    readonly onEdit: (provider: ModelProvider) => void;
    readonly onChoose: (provider: ModelProvider) => void;
}

/**
 * The provider in use is the one thing this screen is about, so it gets the card; the others are a quiet list
 * beneath it, each with the same two buttons.
 */
export function ProviderCards({ providers, onEdit, onChoose }: ProviderCardsProps) {
    const active = providers.find((provider) => provider.active);
    const others = providers.filter((provider) => !provider.active);
    return (
        <Stack gap="section-gap">
            <InUse provider={active} onEdit={onEdit} />
            {others.length > 0 ? <Others providers={others} onEdit={onEdit} onChoose={onChoose} /> : null}
        </Stack>
    );
}

function InUse({ provider, onEdit }: { readonly provider: ModelProvider | undefined; readonly onEdit: (provider: ModelProvider) => void }) {
    const t = useStrings("models");
    if (provider === undefined) {
        return (
            <Surface variant="elevated" className="p-group-gap">
                <Stack gap="inline-tight">
                    <Heading level={2} variant="title3">{t("noneTitle")}</Heading>
                    <Text variant="body" color="secondary">{t("none")}</Text>
                </Stack>
            </Surface>
        );
    }
    return (
        <Surface variant="elevated" className="p-group-gap">
            <Stack gap="group-gap">
                <Row gap="inline" className="justify-between">
                    <Stack gap="inline-tight">
                        <Row gap="inline"><Badge tone="success">{t("inUse")}</Badge></Row>
                        <Heading level={2} variant="title2">{provider.name}</Heading>
                    </Stack>
                    <Button tone="neutral" onClick={() => { onEdit(provider); }}>{t("edit")}</Button>
                </Row>
                <KeyValueList
                    items={[
                        { label: t("endpoint"), value: <Text variant="small">{provider.baseUrl ?? "api.anthropic.com"}</Text> },
                        { label: t("fastModel"), value: <Text variant="small">{provider.fast}</Text> },
                        { label: t("carefulModel"), value: <Text variant="small">{provider.careful}</Text> },
                        { label: t("pricePer"), value: <Text variant="small">{t("priceLine", { priceIn: provider.priceIn, priceOut: provider.priceOut })}</Text> },
                        { label: t("keyLabel"), value: <Text variant="small">{provider.keyHint}</Text> },
                    ]}
                />
            </Stack>
        </Surface>
    );
}

function Others({ providers, onEdit, onChoose }: ProviderCardsProps) {
    const t = useStrings("models");
    return (
        <Stack gap="stack">
            <Stack gap="inline-tight">
                <Heading level={2} variant="title3">{t("others")}</Heading>
                <Text variant="small" color="secondary">{t("othersLead")}</Text>
            </Stack>
            <Surface variant="elevated">
                {providers.map((provider, index) => (
                    <Stack key={provider.id} gap="stack">
                        {index > 0 ? <Separator /> : null}
                        <Row gap="stack" className="justify-between px-group-gap pb-stack first:pt-stack">
                            <Stack gap="inline-tight">
                                <Text variant="bodyStrong">{provider.name}</Text>
                                <Text variant="small" color="secondary">{t("modelsShort", { fast: provider.fast, careful: provider.careful })}</Text>
                            </Stack>
                            <Row gap="inline">
                                <Button tone="neutral" size="sm" onClick={() => { onChoose(provider); }}>{t("use")}</Button>
                                <Button tone="ghost" size="sm" onClick={() => { onEdit(provider); }}>{t("edit")}</Button>
                            </Row>
                        </Row>
                    </Stack>
                ))}
            </Surface>
        </Stack>
    );
}
