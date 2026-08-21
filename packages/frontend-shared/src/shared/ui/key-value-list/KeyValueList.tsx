import type { ReactNode } from "react";
import { Row } from "../row";
import { Stack } from "../stack";
import { Text } from "../text";

export interface KeyValueItem {
    readonly label: string;
    /** Whatever the caller already built — a plain string, a `Badge`, a `Numeric`/`Money`. `KeyValueList` lays it out, it never re-styles it. */
    readonly value: ReactNode;
}

export interface KeyValueListProps {
    readonly items: readonly KeyValueItem[];
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * Tier 1 — composes `Stack`/`Row`/`Text` (Tier 0) into label-value pairs:
 * parsed job fields, the compensation breakdown. One `Row` per item, label
 * and value pushed to opposite ends via `justify-between` so the column of
 * labels reads against the column of values instead of running the two
 * together — `Row`'s own cross-axis centring (`items-center`) still applies
 * unmodified, so a taller value (e.g. a `Badge`) still sits level with its
 * label rather than pinned to its own top edge.
 *
 * `item.label` is used as the React key, not the array index — labels are
 * the natural identity of a key-value pair (two rows sharing one label
 * would be a data problem this component cannot solve anyway), and unlike
 * an index it survives the list being filtered or reordered by the caller.
 */
export function KeyValueList({ items, className }: KeyValueListProps) {
    return (
        <Stack gap="stack-tight" {...(className ? { className } : {})}>
            {items.map((item) => (
                <Row key={item.label} gap="inline" className="justify-between">
                    <Text variant="small" color="secondary">
                        {item.label}
                    </Text>
                    {item.value}
                </Row>
            ))}
        </Stack>
    );
}
