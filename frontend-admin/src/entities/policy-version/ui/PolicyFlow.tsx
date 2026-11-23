import { Fragment } from "react";
import { Badge } from "frontend-shared/ui/badge";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import type { PolicyForm } from "../model/PolicyForm";

export interface PolicyFlowProps {
    readonly form: PolicyForm;
    /** One quiet line of names instead of a box per step; for a place that shows the policy as a consequence of what is being edited. */
    readonly compact?: boolean;
}

/** The steps of a policy as a chain a person walks along: each step the factors that pass it, joined by "or", and whom it is asked of when not everyone. */
export function PolicyFlow({ form, compact = false }: PolicyFlowProps) {
    const factors = useStrings("factors");
    const necessity = useStrings("necessity");
    const t = useStrings("policyFlow");

    return (
        <Row gap="inline" className="flex-wrap">
            {form.mapSteps((step, position) => (
                <Fragment key={position}>
                    {position > 0 ? <Text variant="body" color="muted" aria-hidden="true">{t("arrow")}</Text> : null}
                    {compact ? (
                        <Text variant="body">
                            {step.mapFactors((factor) => factors(factor)).join(` ${t("or")} `)}
                            {step.appliesTo("when_enrolled") ? ` (${necessity("when_enrolled")})` : ""}
                        </Text>
                    ) : (
                        <Surface variant="inset" className="px-stack py-inline">
                            <Stack gap="inline-tight">
                                <Row gap="inline-tight" className="flex-wrap">
                                    {step.mapFactors((factor, at) => (
                                        <Fragment key={factor}>
                                            {at > 0 ? <Text variant="small" color="muted">{t("or")}</Text> : null}
                                            <Badge tone="neutral">{factors(factor)}</Badge>
                                        </Fragment>
                                    ))}
                                </Row>
                                {step.appliesTo("when_enrolled") ? (
                                    <Text variant="caption" color="muted">{necessity("when_enrolled")}</Text>
                                ) : null}
                            </Stack>
                        </Surface>
                    )}
                </Fragment>
            ))}
        </Row>
    );
}
