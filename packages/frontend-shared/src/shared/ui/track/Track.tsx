export type TrackState = "done" | "now" | "ahead" | "end";

export interface TrackStop {
    readonly key: string;
    readonly label: string;
    readonly state: TrackState;
}

export interface TrackProps {
    readonly stops: readonly TrackStop[];
    /** Names the whole strip for assistive technology, such as "Route to Staff: 3 of 6 stops reached". */
    readonly label: string;
    readonly className?: string;
}

/**
 * The marker for each state. A reached stop is filled, the next one is ringed and larger, the ones ahead are
 * empty, and the destination is square: shape and size carry the state, so it survives without colour.
 */
const MARKER_CLASS: Record<TrackState, string> = {
    done: "size-stack rounded-pill bg-interactive-primary border-2 border-interactive-primary",
    now: "size-group-gap rounded-pill bg-surface-elevated border-2 border-status-attention-text",
    ahead: "size-stack rounded-pill bg-surface-elevated border-2 border-border-strong",
    end: "size-stack rounded-chip bg-surface-elevated border-2 border-border-strong",
};

const LABEL_CLASS: Record<TrackState, string> = {
    done: "text-small text-text-secondary",
    now: "text-small text-status-attention-text font-semibold",
    ahead: "text-small text-text-secondary",
    end: "text-small text-text-secondary",
};

/** The segment that arrives at a stop is drawn solid once that stop is reached. */
const LINE_CLASS: Record<TrackState, string> = {
    done: "before:border-interactive-primary",
    now: "before:border-interactive-primary",
    ahead: "before:border-border-default",
    end: "before:border-border-default",
};

/**
 * Tier 0 — a route drawn as stations on a line: what is behind, what is next, what is ahead. It knows
 * nothing of what a stop is; a caller maps its own domain onto four states.
 *
 * The strip scrolls sideways when the stops do not fit, so a long route on a phone stays legible instead
 * of squeezing its labels.
 */
export function Track({ stops, label, className }: TrackProps) {
    return (
        <div className={["overflow-x-auto", className].filter(Boolean).join(" ")}>
            <ol aria-label={label} className="m-0 flex list-none p-0">
                {stops.map((stop, index) => (
                    <li
                        key={stop.key}
                        aria-current={stop.state === "now" ? "step" : undefined}
                        className={[
                            "relative flex shrink-0 basis-1/4 flex-col items-center gap-inline-tight text-center sm:shrink sm:grow sm:basis-0",
                            index > 0
                                ? `before:absolute before:top-stack-tight before:right-1/2 before:h-0 before:w-full before:-translate-y-1/2 before:border-t-2 before:border-solid ${LINE_CLASS[stop.state]}`
                                : "",
                        ]
                            .filter(Boolean)
                            .join(" ")}
                    >
                        <span aria-hidden="true" className="relative flex h-group-gap items-center justify-center">
                            <span className={`box-border ${MARKER_CLASS[stop.state]}`} />
                        </span>
                        <span className={LABEL_CLASS[stop.state]}>{stop.label}</span>
                    </li>
                ))}
            </ol>
        </div>
    );
}
