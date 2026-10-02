import type { IdempotencyKeys } from "./IdempotencyKeys";

export class RandomIdempotencyKeys implements IdempotencyKeys {
    public fresh(): string {
        return crypto.randomUUID();
    }
}
