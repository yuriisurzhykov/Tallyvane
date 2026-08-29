import { Panel } from "frontend-shared/ui/panel";
import { Stack } from "frontend-shared/ui/stack";
import { Row } from "frontend-shared/ui/row";
import { Text } from "frontend-shared/ui/text";
import { Badge, type BadgeTone } from "frontend-shared/ui/badge";
import type { TodayAction, TodayActionUrgency } from "../model/TodayAction";

const URGENCY_LABEL: Record<TodayActionUrgency, string> = {
    now: "Now",
    soon: "Soon",
    later: "Later",
};

const URGENCY_TONE: Record<TodayActionUrgency, BadgeTone> = {
    now: "danger",
    soon: "attention",
    later: "neutral",
};

export interface TodayActionsProps {
    readonly actions: readonly TodayAction[];
}

/**
 * Full mode's recommended-actions widget (ARCHITECTURE.md §12.5, §12.9). One
 * `Panel` per action, ordered as received — sorting by urgency is a
 * real-data decision (`registry-owns-branching`'s own reasoning: a `when`
 * over a status belongs to whoever owns the branching, not to a
 * presentational widget), left to whatever eventually supplies `actions`
 * from a real job-search API.
 */
export function TodayActions({ actions }: TodayActionsProps) {
    return (
        <Stack gap="stack">
            {actions.map((action) => (
                <Panel
                    key={action.id}
                    header={
                        <Row gap="inline" className="justify-between">
                            <Text variant="bodyStrong">{action.title}</Text>
                            <Badge tone={URGENCY_TONE[action.urgency]}>{URGENCY_LABEL[action.urgency]}</Badge>
                        </Row>
                    }
                >
                    <Text variant="body" color="secondary">
                        {action.detail}
                    </Text>
                </Panel>
            ))}
        </Stack>
    );
}
