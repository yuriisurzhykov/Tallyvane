/** Where keys come from. A seam for tests and for nothing else. */
export interface IdempotencyKeys {
    fresh(): string;
}
