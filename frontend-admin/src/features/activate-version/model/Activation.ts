/** What the in-place panel needs of "putting a version in force": a look at what it would do, then the doing. */
export interface Activation<Consequences extends Harmless> {
    preview(): Promise<Consequences>;
    confirm(comment: string): Promise<void>;
}

/** Whether the consequences are worth a warning. */
export interface Harmless {
    isHarmless(): boolean;
}
