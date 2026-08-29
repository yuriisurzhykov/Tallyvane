export type TodayActionUrgency = "now" | "soon" | "later";

/**
 * One recommended action for Today (ARCHITECTURE.md §12.5). Local to this
 * widget rather than a real entity: `entities/job` and friends are still
 * `.gitkeep` (no backend job-search API exists yet to back them), and
 * inventing an entity module for data that is currently static/mock would
 * be a guess at that future shape, not a reflection of anything the backend
 * actually returns yet.
 */
export interface TodayAction {
    readonly id: string;
    readonly title: string;
    readonly detail: string;
    readonly urgency: TodayActionUrgency;
}
